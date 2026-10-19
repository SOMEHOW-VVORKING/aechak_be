package com.aechak.batch.order

import com.aechak.batch.support.BatchIntegrationTestBase
import com.aechak.domain.order.claim.Claim
import com.aechak.domain.order.claim.ClaimItem
import com.aechak.domain.order.claim.enums.ClaimStatus
import com.aechak.domain.order.claim.enums.ClaimType
import com.aechak.domain.order.group.DeliveryAddressSnapshot
import com.aechak.domain.order.group.OrderGroup
import com.aechak.domain.order.order.Order
import com.aechak.domain.order.order.OrderItem
import com.aechak.domain.order.order.OrderStatusHistory
import com.aechak.domain.order.order.enums.ConfirmType
import com.aechak.domain.order.order.enums.OrderStatus
import com.aechak.domain.order.order.enums.TriggeredBy
import com.aechak.domain.user.point.PointTransaction
import com.aechak.domain.user.user.User
import com.aechak.domain.user.user.enums.UserStatus
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * 구매확정 자동 배치 통합. 확정은 비가역이고 반품 기한·정산의 기산점이라
 * 유보 조건(기한 미도래·진행 중 클레임)과 적립 멱등, 커서 전진을 Testcontainers 실 MySQL로 고정한다.
 * 깨지면 자동확정이 앞질러 확정하거나 적립금을 두 번 지급한다는 뜻이다.
 */
class OrderAutoConfirmScheduleIntegrationTest : BatchIntegrationTestBase() {
    @Autowired
    private lateinit var schedule: OrderAutoConfirmSchedule

    private var buyerId = 0L
    private var sig = 0

    @BeforeEach
    fun seedBuyer() {
        buyerId = createActiveUser()
    }

    @Test
    fun `기한이 지난 배송완료 주문은 자동확정되고 확정 유형과 이력 주체가 시스템으로 남는다`() {
        val fixture = seedOrder(autoConfirmDueAt = LocalDateTime.now().minusMinutes(1))

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
        val fixture = seedOrder(autoConfirmDueAt = LocalDateTime.now().minusMinutes(1))

        schedule.runDaily()

        // 10,000 × 2 − 500 = 19,500 → 0.5%는 97.5원이고 1원 미만은 버린다
        assertEquals(listOf(97L), rewardAmounts(fixture.orderPublicId), "수동 확정과 같은 EARN:PURCHASE 키로 1행만 남아야 한다")
        assertEquals(97L, pointBalance(buyerId))
    }

    @Test
    fun `기한이 아직 안 된 주문은 자동확정되지 않는다`() {
        val fixture = seedOrder(autoConfirmDueAt = LocalDateTime.now().plusDays(3))

        schedule.runDaily()

        assertEquals(OrderStatus.DELIVERED, findOrder(fixture.orderId).status)
        assertEquals(emptyList(), rewardAmounts(fixture.orderPublicId), "확정되지 않은 주문에 적립이 생기면 안 된다")
    }

    @Test
    fun `진행 중 클레임이 있는 주문은 기한이 지나도 자동확정에서 빠진다`() {
        val fixture = seedOrder(autoConfirmDueAt = LocalDateTime.now().minusDays(1))
        seedClaim(fixture.orderId, ClaimStatus.CLAIM_REQUESTED)

        schedule.runDaily()

        assertEquals(OrderStatus.DELIVERED, findOrder(fixture.orderId).status, "클레임이 결론나기 전에 확정하면 되돌릴 경로가 없다")
        assertEquals(emptyList(), rewardAmounts(fixture.orderPublicId))
    }

    @Test
    fun `결론난 클레임만 있는 주문은 자동확정된다`() {
        val fixture = seedOrder(autoConfirmDueAt = LocalDateTime.now().minusDays(1))
        seedClaim(fixture.orderId, ClaimStatus.REJECTED)

        schedule.runDaily()

        assertEquals(OrderStatus.PURCHASE_CONFIRMED, findOrder(fixture.orderId).status)
    }

    @Test
    fun `두 번 돌아도 적립 원장은 1행이고 이력도 1행이다`() {
        val fixture = seedOrder(autoConfirmDueAt = LocalDateTime.now().minusMinutes(1))

        schedule.runDaily()
        schedule.runDaily()

        assertEquals(listOf(97L), rewardAmounts(fixture.orderPublicId), "재실행이 적립을 두 번 지급하면 안 된다")
        assertEquals(97L, pointBalance(buyerId))
        assertEquals(1, histories(fixture.orderId).size)
    }

    @Test
    fun `스캔 크기를 넘는 대상도 커서를 밀어 회차 안에서 전부 확정한다`() {
        // 테스트 설정의 scan-size는 2 — 커서가 전진하지 않으면 앞 두 건만 확정되고 멈춘다
        val fixtures = (1..5).map { seedOrder(autoConfirmDueAt = LocalDateTime.now().minusMinutes(1)) }

        schedule.runDaily()

        assertEquals(
            List(5) { OrderStatus.PURCHASE_CONFIRMED },
            fixtures.map { findOrder(it.orderId).status },
        )
        assertEquals(5 * 97L, pointBalance(buyerId))
    }

    @Test
    fun `적립이 실패한 앞자리 건은 확정까지 되돌리고 뒷건은 그대로 확정된다`() {
        // 적립 대상 구매자 행이 없어 적립 단계에서 실패하는 주문 — 앞자리에 둔다
        val blocked = seedOrder(autoConfirmDueAt = LocalDateTime.now().minusMinutes(1), buyer = MISSING_USER_ID)
        val target = seedOrder(autoConfirmDueAt = LocalDateTime.now().minusMinutes(1))

        schedule.runDaily()

        assertEquals(OrderStatus.DELIVERED, findOrder(blocked.orderId).status, "적립이 실패하면 확정 전이도 함께 롤백돼야 한다")
        assertEquals(OrderStatus.PURCHASE_CONFIRMED, findOrder(target.orderId).status, "앞자리 실패가 뒷건을 막으면 안 된다")
        assertEquals(97L, pointBalance(buyerId))
    }

    @Test
    fun `기한이 하루 안으로 다가온 주문에 사전 안내 시각을 남기고 재실행해도 덮지 않는다`() {
        val fixture = seedOrder(autoConfirmDueAt = LocalDateTime.now().plusHours(5))

        schedule.runDaily()
        val firstNotifiedAt = findOrder(fixture.orderId).purchaseConfirmNotifiedAt
        schedule.runDaily()

        assertNotNull(firstNotifiedAt, "기한 D-1 구간의 주문에는 안내 시각이 남아야 한다")
        assertEquals(firstNotifiedAt, findOrder(fixture.orderId).purchaseConfirmNotifiedAt, "재실행이 안내 시각을 덮으면 안 된다")
        assertEquals(OrderStatus.DELIVERED, findOrder(fixture.orderId).status, "안내는 확정이 아니다")
    }

    @Test
    fun `안내 구간보다 먼 주문에는 사전 안내 시각을 남기지 않는다`() {
        val fixture = seedOrder(autoConfirmDueAt = LocalDateTime.now().plusDays(3))

        schedule.runDaily()

        assertNull(findOrder(fixture.orderId).purchaseConfirmNotifiedAt)
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
        val order = em.find(Order::class.java, orderId)
        val claim =
            Claim.request(
                claimType = ClaimType.RETURN,
                order = order,
                items = listOf(ClaimItem.of(order.items.first(), quantity = 1, refundAmount = 10_000L)),
            )
        em.persist(claim)
        em.flush()
        return claim.id
    }

    private fun seedOrder(
        autoConfirmDueAt: LocalDateTime,
        buyer: Long = buyerId,
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
                    expiresAt = LocalDateTime.now().plusMinutes(10),
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
                    sellerId = SELLER_USER_ID,
                    sellerNameSnapshot = "store-$SELLER_USER_ID",
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

    private companion object {
        private const val SELLER_USER_ID = 9_001L

        /** 적립 단계 실패를 주입하는 값 — 이 id의 사용자 행은 심지 않는다 */
        private const val MISSING_USER_ID = 9_999_001L
    }
}
