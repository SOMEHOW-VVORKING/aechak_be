package com.aechak.batch.order

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.aechak.api.support.SettlementFeePolicyFixture
import com.aechak.application.order.usecase.OrderUseCase
import com.aechak.application.order.usecase.command.AutoConfirmPurchaseCommand
import com.aechak.application.order.usecase.command.MarkConfirmNotifiedCommand
import com.aechak.application.order.usecase.query.ConfirmNotifyScanQuery
import com.aechak.batch.support.BatchIntegrationTestBase
import com.aechak.common.error.BusinessException
import com.aechak.domain.order.claim.Claim
import com.aechak.domain.order.claim.ClaimItem
import com.aechak.domain.order.claim.enums.ClaimStatus
import com.aechak.domain.order.claim.enums.ClaimType
import com.aechak.domain.order.error.OrderErrorCode
import com.aechak.domain.order.group.DeliveryAddressSnapshot
import com.aechak.domain.order.group.OrderGroup
import com.aechak.domain.order.order.Order
import com.aechak.domain.order.order.OrderItem
import com.aechak.domain.order.order.OrderStatusHistory
import com.aechak.domain.order.order.enums.ConfirmType
import com.aechak.domain.order.order.enums.OrderStatus
import com.aechak.domain.order.order.enums.TriggeredBy
import com.aechak.domain.settlement.error.SettlementErrorCode
import com.aechak.domain.settlement.feepolicy.SettlementFeePolicy
import com.aechak.domain.settlement.settlement.Settlement
import com.aechak.domain.settlement.settlement.enums.SettlementStatus
import com.aechak.domain.settlement.settlement.policy.SettlementCalendar
import com.aechak.domain.user.point.PointTransaction
import com.aechak.domain.user.user.User
import com.aechak.domain.user.user.enums.UserStatus
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import ch.qos.logback.classic.Level as LogLevel
import ch.qos.logback.classic.Logger as LogbackLogger

/**
 * 구매확정 자동 배치 통합. 확정은 비가역이고 반품 기한·정산의 기산점이라
 * 유보 조건(기한 미도래·진행 중 클레임)과 적립·정산 원장의 멱등, 커서 전진을 Testcontainers 실 MySQL로 고정한다.
 * 깨지면 자동확정이 앞질러 확정하거나, 적립금을 두 번 지급하거나, 셀러에게 판매 대금이 지급되지 않는다는 뜻이다.
 */
class OrderAutoConfirmScheduleIntegrationTest : BatchIntegrationTestBase() {
    @Autowired
    private lateinit var schedule: OrderAutoConfirmSchedule

    @Autowired
    private lateinit var orderUseCase: OrderUseCase

    private var buyerId = 0L
    private var sig = 0

    @BeforeEach
    fun seedConfirmPrerequisites() {
        buyerId = createActiveUser()
        seedGlobalSettlementFeePolicy()
    }

    @Test
    fun `기한이 지난 배송완료 주문은 자동확정되고 확정 유형과 이력 주체가 시스템으로 남는다`() {
        val fixture = seedOrder(autoConfirmDueAt = kstNow().minusMinutes(1))

        schedule.runDaily()

        val order = findOrder(fixture.orderId)
        assertEquals(OrderStatus.PURCHASE_CONFIRMED, order.status)
        assertEquals(ConfirmType.AUTO, order.confirmType, "배치가 확정한 건은 AUTO여야 한다")
        assertNotNull(order.purchaseConfirmedAt, "확정 시각이 남아야 한다")
        assertEquals(
            listOf(Triple(OrderStatus.DELIVERED, OrderStatus.PURCHASE_CONFIRMED, TriggeredBy.SYSTEM)),
            histories(fixture.orderId),
        )
    }

    @Test
    fun `자동확정은 수동 확정과 같은 멱등키로 적립금을 지급한다`() {
        val fixture = seedOrder(autoConfirmDueAt = kstNow().minusMinutes(1))

        schedule.runDaily()

        // 10,000 × 2 − 500 = 19,500 → 0.5%는 97.5원이고 1원 미만은 버린다
        assertEquals(listOf(97L), rewardAmounts(fixture.orderPublicId), "수동 확정과 같은 EARN:PURCHASE 키로 1행만 남아야 한다")
        assertEquals(97L, pointBalance(buyerId))
    }

    @Test
    fun `자동확정한 주문에도 정산 원장이 생기고 금액이 요율 정책대로 박힌다`() {
        // 원장이 없으면 정산 집행 배치가 그 주문을 읽지 못해 셀러에게 판매 대금이 지급되지 않는다
        val fixture = seedOrder(autoConfirmDueAt = kstNow().minusMinutes(1))

        schedule.runDaily()

        val settlement = settlements(fixture.orderId).single()
        // 10,000 × 2 − 500 = 19,500. 수수료 8% = 1,560 / PG 2.8% = 546.
        // 배송비 3,000은 요율 기준에서 빠지고 지급액에만 실비로 더해진다 → 19,500 + 3,000 − 1,560 − 546 = 20,394
        assertEquals(19_500L, settlement.grossAmount)
        assertEquals(3_000L, settlement.shippingFee)
        assertEquals(1_560L, settlement.commission)
        assertEquals(546L, settlement.pgFee)
        assertEquals(20_394L, settlement.netAmount)
        assertEquals(SELLER_USER_ID, settlement.sellerId)
        assertEquals(SettlementStatus.PENDING, settlement.status)
        assertEquals(Settlement.idempotencyKeyOf(settlement.orderItemId), settlement.idempotencyKey)

        // 정산월과 지급 예정일은 배치가 기록한 확정 시각에서 파생된다
        val confirmedOn = SettlementCalendar.settlementDateOf(findOrder(fixture.orderId).purchaseConfirmedAt!!)
        assertEquals(Settlement.settlementMonthOf(confirmedOn), settlement.settlementMonth)
        assertEquals(Settlement.expectedPayoutDateOf(confirmedOn), settlement.expectedPayoutDate)
    }

    @Test
    fun `기한이 아직 안 된 주문은 자동확정되지 않는다`() {
        val fixture = seedOrder(autoConfirmDueAt = kstNow().plusDays(3))

        schedule.runDaily()

        assertEquals(OrderStatus.DELIVERED, findOrder(fixture.orderId).status)
        assertEquals(emptyList(), rewardAmounts(fixture.orderPublicId), "확정되지 않은 주문에 적립이 생기면 안 된다")
        assertEquals(emptyList(), settlements(fixture.orderId), "확정되지 않은 주문에 정산 원장이 생기면 안 된다")
    }

    @Test
    fun `진행 중 클레임이 있는 주문은 기한이 지나도 자동확정에서 빠진다`() {
        val fixture = seedOrder(autoConfirmDueAt = kstNow().minusDays(1))
        seedClaim(fixture.orderId, ClaimStatus.CLAIM_REQUESTED)

        schedule.runDaily()

        assertEquals(OrderStatus.DELIVERED, findOrder(fixture.orderId).status, "클레임이 결론나기 전에 확정하면 되돌릴 경로가 없다")
        assertEquals(emptyList(), rewardAmounts(fixture.orderPublicId))
    }

    @Test
    fun `결론난 클레임만 있는 주문은 자동확정된다`() {
        val fixture = seedOrder(autoConfirmDueAt = kstNow().minusDays(1))
        seedClaim(fixture.orderId, ClaimStatus.REJECTED)

        schedule.runDaily()

        assertEquals(OrderStatus.PURCHASE_CONFIRMED, findOrder(fixture.orderId).status)
    }

    @Test
    fun `두 번 돌아도 적립·정산 원장은 1행이고 이력도 1행이다`() {
        val fixture = seedOrder(autoConfirmDueAt = kstNow().minusMinutes(1))

        schedule.runDaily()
        schedule.runDaily()

        assertEquals(listOf(97L), rewardAmounts(fixture.orderPublicId), "재실행이 적립을 두 번 지급하면 안 된다")
        assertEquals(97L, pointBalance(buyerId))
        assertEquals(1, histories(fixture.orderId).size)
        assertEquals(1, settlements(fixture.orderId).size, "재실행이 정산 원장을 늘리면 셀러에게 두 번 지급된다")
    }

    @Test
    fun `스캔 크기를 넘는 대상도 회차 안에서 전부 확정한다`() {
        val fixtures = (1..5).map { seedOrder(autoConfirmDueAt = kstNow().minusMinutes(1)) }

        schedule.runDaily()

        assertEquals(
            List(5) { OrderStatus.PURCHASE_CONFIRMED },
            fixtures.map { findOrder(it.orderId).status },
        )
        assertEquals(5 * 97L, pointBalance(buyerId))
    }

    @Test
    fun `첫 페이지가 전부 실패로 남아도 커서가 전진해 다음 페이지를 처리한다`() {
        // 테스트 설정의 scan-size는 2. 앞 두 건은 적립이 실패해 DELIVERED로 남아 스캔 대상에서 빠지지 않는다 —
        // 커서가 전진하지 않으면 같은 두 건만 다시 읽으며 세 번째 건에 영원히 닿지 못한다
        val blocked = (1..2).map { seedOrder(autoConfirmDueAt = kstNow().minusMinutes(1), buyer = MISSING_USER_ID) }
        val target = seedOrder(autoConfirmDueAt = kstNow().minusMinutes(1))

        schedule.runDaily()

        assertEquals(List(2) { OrderStatus.DELIVERED }, blocked.map { findOrder(it.orderId).status })
        assertEquals(OrderStatus.PURCHASE_CONFIRMED, findOrder(target.orderId).status, "커서가 전진해야 뒷 페이지에 닿는다")
    }

    @Test
    fun `적립이 실패한 앞자리 건은 확정까지 되돌리고 뒷건은 그대로 확정된다`() {
        // 적립 대상 구매자 행이 없어 적립 단계에서 실패하는 주문 — 앞자리에 둔다
        val blocked = seedOrder(autoConfirmDueAt = kstNow().minusMinutes(1), buyer = MISSING_USER_ID)
        val target = seedOrder(autoConfirmDueAt = kstNow().minusMinutes(1))

        schedule.runDaily()

        assertEquals(OrderStatus.DELIVERED, findOrder(blocked.orderId).status, "적립이 실패하면 확정 전이도 함께 롤백돼야 한다")
        assertEquals(OrderStatus.PURCHASE_CONFIRMED, findOrder(target.orderId).status, "앞자리 실패가 뒷건을 막으면 안 된다")
        assertEquals(97L, pointBalance(buyerId))
    }

    @Test
    fun `정산 원장 생성이 실패한 앞자리 건은 확정과 적립까지 되돌리고 뒷건은 그대로 확정된다`() {
        // 전역 요율을 지우고 기본 셀러 전용 요율만 남긴다 — 요율이 없는 셀러의 주문은 원장 생성 단계에서 실패한다
        leaveFeePolicyOnlyFor(SELLER_USER_ID)
        val blocked = seedOrder(autoConfirmDueAt = kstNow().minusMinutes(1), seller = SELLER_WITHOUT_FEE_POLICY)
        val target = seedOrder(autoConfirmDueAt = kstNow().minusMinutes(1))

        schedule.runDaily()

        val blockedOrder = findOrder(blocked.orderId)
        assertEquals(OrderStatus.DELIVERED, blockedOrder.status, "원장을 못 만들면 확정 전이도 함께 롤백돼야 한다")
        assertNull(blockedOrder.purchaseConfirmedAt, "확정 시각이 남으면 그 주문은 다음 회차 스캔에서 빠져 원장 없이 굳는다")
        assertEquals(emptyList(), rewardAmounts(blocked.orderPublicId), "적립도 같은 트랜잭션이라 함께 롤백돼야 한다")
        assertEquals(emptyList(), settlements(blocked.orderId))
        assertEquals(OrderStatus.PURCHASE_CONFIRMED, findOrder(target.orderId).status, "앞자리 실패가 뒷건을 막으면 안 된다")
        assertEquals(1, settlements(target.orderId).size, "뒷건의 원장은 그대로 만들어져야 한다")
        assertEquals(97L, pointBalance(buyerId), "뒷건의 적립만 남아야 한다")
    }

    @Test
    fun `원장 실패는 유보가 아니라 ERROR로 남는다`() {
        // 요율 누락은 설정이 고쳐질 때까지 다음 회차에도 같은 자리에서 막힌다 —
        // 상태 경합과 같은 INFO 유보로 묻히면 셀러 지급이 멈춘 것을 알아챌 길이 없다
        leaveFeePolicyOnlyFor(SELLER_USER_ID)
        val blocked = seedOrder(autoConfirmDueAt = kstNow().minusMinutes(1), seller = SELLER_WITHOUT_FEE_POLICY)

        val logs = runDailyCapturingLogs()

        val event = logs.single { it.level == LogLevel.ERROR }
        assertTrue(event.formattedMessage.contains("orderId=${blocked.orderId}"), "실패 로그에 주문 식별자가 없다: ${event.formattedMessage}")
        assertTrue(
            event.formattedMessage.contains("errorCode=${SettlementErrorCode.SETTLEMENT_FEE_POLICY_NOT_FOUND.code}"),
            "실패 로그에 요율 누락 코드가 없다: ${event.formattedMessage}",
        )
        assertNotNull(event.throwableProxy, "스택이 빠지면 어느 단계에서 터졌는지 로그만으로 알 수 없다")
    }

    @Test
    fun `요율 정책이 복구되면 다음 회차가 같은 주문을 다시 집어 확정·적립·원장을 한 번만 남긴다`() {
        leaveFeePolicyOnlyFor(SELLER_USER_ID)
        val fixture = seedOrder(autoConfirmDueAt = kstNow().minusMinutes(1), seller = SELLER_WITHOUT_FEE_POLICY)
        schedule.runDaily()
        assertEquals(OrderStatus.DELIVERED, findOrder(fixture.orderId).status, "첫 회차는 원장 실패로 확정이 되돌아가야 한다")

        seedGlobalSettlementFeePolicy()
        schedule.runDaily()

        val order = findOrder(fixture.orderId)
        assertEquals(OrderStatus.PURCHASE_CONFIRMED, order.status, "정책이 생기면 다음 회차가 같은 주문을 다시 집어야 한다")
        assertEquals(ConfirmType.AUTO, order.confirmType)
        assertEquals(1, histories(fixture.orderId).size, "되돌아간 회차의 이력이 남아 있으면 안 된다")
        assertEquals(listOf(97L), rewardAmounts(fixture.orderPublicId))
        assertEquals(1, settlements(fixture.orderId).size)
    }

    @Test
    fun `기한이 하루 안으로 다가온 주문에 사전 안내 시각을 남기고 재실행해도 덮지 않는다`() {
        val fixture = seedOrder(autoConfirmDueAt = kstNow().plusHours(5))

        schedule.runDaily()
        val firstNotifiedAt = findOrder(fixture.orderId).purchaseConfirmNotifiedAt
        schedule.runDaily()

        assertNotNull(firstNotifiedAt, "기한 D-1 구간의 주문에는 안내 시각이 남아야 한다")
        assertEquals(firstNotifiedAt, findOrder(fixture.orderId).purchaseConfirmNotifiedAt, "재실행이 안내 시각을 덮으면 안 된다")
        assertEquals(OrderStatus.DELIVERED, findOrder(fixture.orderId).status, "안내는 확정이 아니다")
    }

    @Test
    fun `안내 구간보다 먼 주문에는 사전 안내 시각을 남기지 않는다`() {
        val fixture = seedOrder(autoConfirmDueAt = kstNow().plusDays(3))

        schedule.runDaily()

        assertNull(findOrder(fixture.orderId).purchaseConfirmNotifiedAt)
    }

    @Test
    fun `안내 대상으로 잡힌 뒤 확정된 주문은 안내 기록을 거절하고 확정 컬럼도 그대로다`() {
        // 스캔과 기록 사이에 구매자가 확정한 자리를 그대로 만든다 — 스캔을 거치면 상태 조건에 걸려 이 경로에 닿지 못한다.
        // 안내 갱신이 엔티티 전 컬럼을 덮던 구현으로 돌아가면 확정 상태·유형·시각이 여기서 지워진다
        val fixture = seedOrder(autoConfirmDueAt = kstNow().plusHours(5))
        assertEquals(listOf(fixture.orderId), notifyTargetIds(), "확정 전에는 안내 대상이다")
        confirmManually(fixture.orderId)

        val recorded = orderUseCase.markConfirmNotified(MarkConfirmNotifiedCommand(fixture.orderId, kstNow()))

        assertFalse(recorded, "확정된 주문의 안내 기록은 거절돼야 한다")
        val order = findOrder(fixture.orderId)
        assertEquals(OrderStatus.PURCHASE_CONFIRMED, order.status, "안내가 확정 상태를 되돌리면 안 된다")
        assertEquals(ConfirmType.MANUAL, order.confirmType, "안내가 확정 유형을 지우면 안 된다")
        assertNotNull(order.purchaseConfirmedAt)
        assertNull(order.purchaseConfirmNotifiedAt)
    }

    @Test
    fun `이미 안내한 주문의 재기록은 거절되고 최초 안내 시각이 남는다`() {
        val fixture = seedOrder(autoConfirmDueAt = kstNow().plusHours(5))
        val first = kstNow()
        orderUseCase.markConfirmNotified(MarkConfirmNotifiedCommand(fixture.orderId, first))

        val again = orderUseCase.markConfirmNotified(MarkConfirmNotifiedCommand(fixture.orderId, first.plusHours(1)))

        assertFalse(again)
        assertEquals(first, findOrder(fixture.orderId).purchaseConfirmNotifiedAt)
    }

    @Test
    fun `기한이 아직 안 된 주문은 자동확정 유스케이스를 직접 불러도 거절된다`() {
        val fixture = seedOrder(autoConfirmDueAt = kstNow().plusDays(3))

        val exception =
            assertFailsWith<BusinessException> {
                orderUseCase.autoConfirmPurchase(AutoConfirmPurchaseCommand(fixture.orderId, kstNow()))
            }

        assertEquals(OrderErrorCode.ORDER_NOT_CONFIRMABLE, exception.errorCode)
        assertEquals(OrderStatus.DELIVERED, findOrder(fixture.orderId).status, "거절된 확정이 선점을 남기면 안 된다")
    }

    private fun createActiveUser(): Long =
        tx.execute {
            val user = User.preRegister()
            em.persist(user)
            em.flush()
            em
                .createQuery("update User u set u.status = :st where u.id = :id")
                .setParameter("st", UserStatus.ACTIVE)
                .setParameter("id", user.id)
                .executeUpdate()
            user.id
        }!!

    private fun notifyTargetIds(): List<Long> {
        val now = kstNow()
        return orderUseCase.findConfirmNotifyTargetIds(
            ConfirmNotifyScanQuery(dueAtAfter = now, dueAtOrBefore = now.plusDays(1), afterOrderId = 0L, limit = 10),
        )
    }

    /** 구매자 확정을 흉내낸다 — 확정 경로는 api 소관이라 전이·확정 컬럼만 맞춘다. */
    private fun confirmManually(orderId: Long) =
        tx.execute {
            em
                .createQuery(
                    "update Order o set o.status = :status, o.confirmType = :type, o.purchaseConfirmedAt = :at where o.id = :id",
                ).setParameter("status", OrderStatus.PURCHASE_CONFIRMED)
                .setParameter("type", ConfirmType.MANUAL)
                .setParameter("at", kstNow())
                .setParameter("id", orderId)
                .executeUpdate()
        }

    private fun findOrder(orderId: Long): Order =
        tx.execute {
            em.find(Order::class.java, orderId)
        }!!

    private fun histories(orderId: Long): List<Triple<OrderStatus, OrderStatus, TriggeredBy>> =
        tx.execute {
            em
                .createQuery(
                    "select h from OrderStatusHistory h where h.order.id = :orderId order by h.id",
                    OrderStatusHistory::class.java,
                ).setParameter("orderId", orderId)
                .resultList
                .map { Triple(it.fromStatus!!, it.toStatus, it.triggeredBy) }
        }!!

    /** 스케줄 로거에 임시 어펜더를 달고 한 회차를 돌린 뒤 그 회차가 남긴 로그 이벤트를 돌려준다. */
    private fun runDailyCapturingLogs(): List<ILoggingEvent> {
        val logger = LoggerFactory.getLogger(OrderAutoConfirmSchedule::class.java) as LogbackLogger
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        logger.addAppender(appender)
        try {
            schedule.runDaily()
        } finally {
            logger.detachAppender(appender)
            appender.stop()
        }
        return appender.list.toList()
    }

    private fun settlements(orderId: Long): List<Settlement> =
        tx.execute {
            em
                .createQuery(
                    "select s from Settlement s where s.orderId = :orderId order by s.orderItemId",
                    Settlement::class.java,
                ).setParameter("orderId", orderId)
                .resultList
        }!!

    /** 전역 요율을 지우고 주어진 셀러 전용 요율만 남긴다 — 다른 셀러의 주문은 요율을 찾지 못한다. */
    private fun leaveFeePolicyOnlyFor(sellerId: Long) =
        tx.execute {
            em.createQuery("delete from SettlementFeePolicy").executeUpdate()
            em.persist(
                SettlementFeePolicy.of(
                    sellerId,
                    SettlementFeePolicyFixture.EFFECTIVE_FROM,
                    SettlementFeePolicyFixture.PLATFORM_FEE_RATE,
                    SettlementFeePolicyFixture.PG_FEE_RATE,
                ),
            )
            em.flush()
        }

    private fun rewardAmounts(orderPublicId: String): List<Long> =
        tx.execute {
            em
                .createQuery(
                    "select t from PointTransaction t where t.idempotencyKey = :key",
                    PointTransaction::class.java,
                ).setParameter("key", "EARN:PURCHASE:$orderPublicId")
                .resultList
                .map { it.amount }
        }!!

    private fun pointBalance(userId: Long): Long =
        tx.execute {
            em.find(User::class.java, userId).pointBalance
        }!!

    private fun seedClaim(
        orderId: Long,
        status: ClaimStatus,
    ) = tx.execute {
        val claimId = persistClaim(orderId)
        // 요청 상태 외의 상태는 전이 경로가 아직 없어 쿼리로 심는다 — 전이 규칙 자체는 이 테스트의 대상이 아니다
        em
            .createQuery("update Claim c set c.status = :status where c.id = :id")
            .setParameter("status", status)
            .setParameter("id", claimId)
            .executeUpdate()
    }

    private fun persistClaim(orderId: Long): Long {
        // 클레임 요청은 발송 전 상태만 받는다. 확정 유보만 보는 이 테스트는 주문을 잠시 결제완료로 내려 심고 되돌린다
        val restored = swapOrderStatus(orderId, OrderStatus.PAID)
        em.clear()
        val order = em.find(Order::class.java, orderId)
        val claim =
            Claim.request(
                claimType = ClaimType.CANCEL,
                order = order,
                reasonCode = "CHANGE_OF_MIND",
                reasonDetail = null,
                items = listOf(ClaimItem.of(order.items.first(), quantity = 1, refundAmount = 10_000L)),
                autoApproveDueAt = kstNow().plusDays(2),
            )
        em.persist(claim)
        em.flush()
        swapOrderStatus(orderId, restored)
        return claim.id
    }

    /** 주문 상태를 바꾸고 이전 상태를 돌려준다. 벌크 JPQL이라 상태 이력에는 남지 않는다. */
    private fun swapOrderStatus(
        orderId: Long,
        next: OrderStatus,
    ): OrderStatus {
        em.flush()
        val previous =
            em
                .createQuery("select o.status from Order o where o.id = :id", OrderStatus::class.java)
                .setParameter("id", orderId)
                .singleResult
        em
            .createQuery("update Order o set o.status = :status where o.id = :id")
            .setParameter("status", next)
            .setParameter("id", orderId)
            .executeUpdate()
        return previous
    }

    private fun seedOrder(
        autoConfirmDueAt: LocalDateTime,
        buyer: Long = buyerId,
        seller: Long = SELLER_USER_ID,
        status: OrderStatus = OrderStatus.DELIVERED,
        unitPrice: Long = 10_000L,
        quantity: Int = 2,
        discountAllocatedAmount: Long = 500L,
    ): Fixture =
        tx.execute {
            val tag = "s${sig++}"
            val group =
                OrderGroup.create(
                    buyerId = buyer,
                    deliveryAddressId = 0L,
                    deliveryAddress = deliveryAddressSnapshot(),
                    usedPoint = 0L,
                    totalProductAmount = unitPrice * quantity,
                    totalShippingFee = 3_000L,
                    idempotencyKey = "idem-$tag-$buyer",
                    expiresAt = kstNow().plusMinutes(10),
                )
            em.persist(group)
            val item =
                OrderItem.of(
                    productId = 1L,
                    optionCombinationId = 1L,
                    optionNameSnapshot = "블랙 / L",
                    quantity = quantity,
                    unitPriceSnapshot = unitPrice,
                    discountAllocatedAmount = discountAllocatedAmount,
                    productVersionId = 1L,
                )
            val order =
                Order.create(
                    orderGroup = group,
                    sellerId = seller,
                    sellerNameSnapshot = "store-$seller",
                    allocatedCouponDiscount = 0L,
                    sellerShippingFee = 3_000L,
                    baseShippingFeeSnapshot = null,
                    freeShippingThresholdSnapshot = null,
                    items = listOf(item),
                )
            em.persist(order)
            em.flush()
            // 배송완료 전이와 기한 부여는 배송완료 경로 소관이라 여기선 쿼리로 상태만 맞춘다
            em
                .createQuery("update Order o set o.status = :status, o.autoConfirmDueAt = :dueAt where o.id = :id")
                .setParameter("status", status)
                .setParameter("dueAt", autoConfirmDueAt)
                .setParameter("id", order.id)
                .executeUpdate()
            Fixture(orderId = order.id, orderPublicId = order.publicId)
        }!!

    private fun deliveryAddressSnapshot() =
        DeliveryAddressSnapshot(
            receiverNameEnc = "enc-receiver",
            contactNumberEnc = "enc-contact",
            zipCode = "12345",
            baseAddress = "서울시 애착구 멍냥로 1",
            detailAddress = null,
            deliveryMemo = null,
        )

    private data class Fixture(
        val orderId: Long,
        val orderPublicId: String,
    )

    /**
     * 배치가 KST로 판정하므로 fixture 시각도 KST로 만든다 — JVM 기본 시간대가 UTC면 기한이 9시간 밀린다.
     * 마이크로초로 끊는 이유는 DB 왕복이다. 시각 컬럼은 DATETIME(6)이라 그보다 작은 자리는 반올림돼 사라지는데,
     * JVM 시계 해상도는 리눅스에서 나노·맥에서 마이크로라 끊지 않으면 심은 값과 읽은 값이 리눅스에서만 어긋난다.
     */
    private fun kstNow(): LocalDateTime = LocalDateTime.now(KST).truncatedTo(ChronoUnit.MICROS)

    private companion object {
        private val KST = ZoneId.of("Asia/Seoul")

        private const val SELLER_USER_ID = 9_001L

        /** 원장 단계 실패를 주입하는 값 — 이 셀러에게는 수수료율 정책을 심지 않는다 */
        private const val SELLER_WITHOUT_FEE_POLICY = 9_002L

        /** 적립 단계 실패를 주입하는 값 — 이 id의 사용자 행은 심지 않는다 */
        private const val MISSING_USER_ID = 9_999_001L
    }
}
