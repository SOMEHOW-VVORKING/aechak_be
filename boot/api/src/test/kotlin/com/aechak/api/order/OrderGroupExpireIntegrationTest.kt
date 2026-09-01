package com.aechak.api.order

import com.aechak.api.consumer.order.PointReleaseOnOrderGroupCancelledConsumer
import com.aechak.api.consumer.order.StockRestoreOnOrderGroupCancelledConsumer
import com.aechak.api.support.FakePaymentGateway
import com.aechak.api.support.KafkaIntegrationTestBase
import com.aechak.application.messaging.MessagePublisher
import com.aechak.application.order.usecase.OrderGroupExpireUseCase
import com.aechak.application.order.usecase.result.ExpireTargetResult
import com.aechak.application.payment.port.PaymentGatewayStatus
import com.aechak.application.payment.port.PaymentGatewayView
import com.aechak.domain.order.group.DeliveryAddressSnapshot
import com.aechak.domain.order.group.OrderGroup
import com.aechak.domain.order.group.enums.OrderGroupStatus
import com.aechak.domain.order.group.repository.OrderGroupRepository
import com.aechak.domain.order.order.Order
import com.aechak.domain.order.order.OrderItem
import com.aechak.domain.order.order.enums.OrderStatus
import com.aechak.domain.payment.enums.PaymentMethod
import com.aechak.domain.payment.enums.PaymentStatus
import com.aechak.domain.product.category.Category
import com.aechak.domain.product.option.OptionCombination
import com.aechak.domain.product.product.Product
import com.aechak.domain.user.point.enums.PointTransactionType
import com.aechak.domain.user.user.User
import com.aechak.domain.user.user.enums.UserStatus
import com.aechak.infra.kafka.Topics
import com.aechak.infra.persistence.payment.PaymentJpaEntity
import com.aechak.message.order.OrderGroupCancelledMessage
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTimeoutPreemptively
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.test.utils.KafkaTestUtils
import java.time.Duration
import java.time.LocalDateTime
import java.util.UUID

/**
 * 주문그룹 TTL 만료 배치 통합. 포트원 조회로 취소 가능 여부를 판정하는 전이와, 취소 메시지를 받은 product·user BC가
 * 재고와 적립금을 되돌리는 계약을 고정함. 복원은 다른 트랜잭션에서 오므로 내장 Kafka 위에서 기다려 단언함.
 * 깨지면 결제창을 닫고 사라진 주문이 재고와 적립금을 영영 잡아 두거나, 반대로 돈이 잡힌 주문이 취소됨.
 * 선점 전이는 조건부 원자 UPDATE라 Testcontainers 실 DB가 전제(H2 금지).
 */
class OrderGroupExpireIntegrationTest : KafkaIntegrationTestBase() {
    @PersistenceContext
    private lateinit var em: EntityManager

    @Autowired
    private lateinit var expireUseCase: OrderGroupExpireUseCase

    @Autowired
    private lateinit var orderGroupRepository: OrderGroupRepository

    @Autowired
    private lateinit var paymentGateway: FakePaymentGateway

    @Autowired
    private lateinit var messagePublisher: MessagePublisher

    @Autowired
    private lateinit var kafka: KafkaTemplate<String, String>

    @BeforeEach
    fun resetGateway() = paymentGateway.clear()

    /** 배치 잡과 같은 규약으로 전량 처리함. 커서를 전진시키고, 실패는 상한까지 건너뛰며 넘으면 이번 실행을 중단함 */
    private fun sweepAll() {
        var cursor: ExpireTargetResult? = null
        var skipped = 0
        do {
            val targets = expireUseCase.findExpireTargets(cursor, CHUNK)
            targets.forEach { runCatching { expireUseCase.cancelIfUnpaid(it) }.onFailure { skipped++ } }
            cursor = targets.lastOrNull() ?: cursor
        } while (targets.size == CHUNK && skipped <= SKIP_LIMIT)
    }

    private data class ItemSeed(
        val optionCombinationId: Long,
        val quantity: Int,
    )

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

    /** 만료 흐름은 셀러·장바구니를 안 읽어 상품과 옵션조합만 심음 */
    private fun seedCombination(stock: Int): Long =
        tx.execute {
            val sig = UUID.randomUUID().toString()
            val category = Category.create(null, 1, "카테고리-$sig", null, 1)
            em.persist(category)
            val product = Product.register(category, 1L, "상품-$sig", null, null, 10_000L, null, null, null)
            em.persist(product)
            val combination = OptionCombination.create(product, "기본 / 1개", 0L, stock, "sig-$sig")
            em.persist(combination)
            combination.id
        }!!

    /** orders의 원소 하나가 셀러 주문 하나. 배송지 스냅샷은 만료 흐름이 안 읽어 암호문 자리에 더미를 넣음 */
    private fun seedGroup(
        buyerId: Long,
        orders: List<List<ItemSeed>> = emptyList(),
        usedPoint: Long = 0L,
        expiresAt: LocalDateTime = LocalDateTime.now().minusMinutes(1),
        mutate: (OrderGroup) -> Unit = {},
    ): Long =
        tx.execute {
            val group =
                OrderGroup.create(
                    buyerId = buyerId,
                    deliveryAddressId = 1L,
                    deliveryAddress =
                        DeliveryAddressSnapshot(
                            receiverNameEnc = "enc-name",
                            contactNumberEnc = "enc-contact",
                            zipCode = "12345",
                            baseAddress = "서울시 애착구 멍냥로 1",
                            detailAddress = "101동 202호",
                            deliveryMemo = null,
                        ),
                    usedPoint = usedPoint,
                    totalProductAmount = 20_000L,
                    totalShippingFee = 0L,
                    idempotencyKey = "key-${UUID.randomUUID()}",
                    expiresAt = expiresAt,
                )
            mutate(group)
            em.persist(group)
            orders.forEachIndexed { index, items ->
                em.persist(
                    Order.create(
                        orderGroup = group,
                        sellerId = (index + 1).toLong(),
                        sellerNameSnapshot = "셀러-${index + 1}",
                        allocatedCouponDiscount = 0,
                        sellerShippingFee = 0,
                        items =
                            items.map {
                                OrderItem.of(
                                    productId = 1L,
                                    optionCombinationId = it.optionCombinationId,
                                    quantity = it.quantity,
                                    unitPriceSnapshot = 10_000L,
                                    discountAllocatedAmount = 0,
                                    productVersionId = 1L,
                                )
                            },
                    ),
                )
            }
            em.flush()
            group.id
        }!!

    private fun seedPayment(orderGroupId: Long) {
        tx.execute {
            em.persist(
                PaymentJpaEntity(
                    id = 0L,
                    orderGroupId = orderGroupId,
                    paymentId = "pay-${UUID.randomUUID()}",
                    method = PaymentMethod.KAKAO_PAY,
                    targetAmount = 20_000L,
                    status = PaymentStatus.PENDING,
                    transactionId = null,
                    realPaidAmount = null,
                    failureCode = null,
                    cancellableAmount = null,
                    version = 0,
                ),
            )
        }
    }

    /** 잔액 갱신 경로를 거치지 않고 캐시 컬럼을 직접 심음 */
    private fun seedPointBalance(
        userId: Long,
        balance: Long,
    ) {
        tx.execute {
            em
                .createQuery("update User u set u.pointBalance = :balance, u.updatedAt = CURRENT_TIMESTAMP where u.id = :id")
                .setParameter("balance", balance)
                .setParameter("id", userId)
                .executeUpdate()
        }
    }

    /** PAID에는 paidAt이 필수라 상태에 따라 승인 흔적을 함께 채움 */
    private fun gatewayView(status: PaymentGatewayStatus): PaymentGatewayView =
        PaymentGatewayView(
            status = status,
            totalAmount = 20_000L,
            paidAmount = if (status == PaymentGatewayStatus.PAID) 20_000L else 0L,
            pgTxId = null,
            paidAt = if (status == PaymentGatewayStatus.PAID) LocalDateTime.of(2026, 1, 1, 0, 0) else null,
        )

    private fun groupStatusOf(groupId: Long): OrderGroupStatus =
        em
            .createQuery("select g.status from OrderGroup g where g.id = :id", OrderGroupStatus::class.java)
            .setParameter("id", groupId)
            .singleResult

    private fun publicIdOf(groupId: Long): String =
        em
            .createQuery("select g.publicId from OrderGroup g where g.id = :id", String::class.java)
            .setParameter("id", groupId)
            .singleResult

    private fun orderStatusesOf(groupId: Long): List<OrderStatus> =
        em
            .createQuery("select o.status from Order o where o.orderGroup.id = :id order by o.id", OrderStatus::class.java)
            .setParameter("id", groupId)
            .resultList

    private fun stockOf(combinationId: Long): Int =
        em
            .createQuery("select oc.stockQuantity from OptionCombination oc where oc.id = :id", java.lang.Integer::class.java)
            .setParameter("id", combinationId)
            .singleResult
            .toInt()

    private fun pointBalanceOf(userId: Long): Long =
        em
            .createQuery("select u.pointBalance from User u where u.id = :id", java.lang.Long::class.java)
            .setParameter("id", userId)
            .singleResult
            .toLong()

    private fun ledgerTypesOf(idempotencyKey: String): List<PointTransactionType> =
        em
            .createQuery(
                "select pt.transactionType from PointTransaction pt where pt.idempotencyKey = :key",
                PointTransactionType::class.java,
            ).setParameter("key", idempotencyKey)
            .resultList

    private fun ledgerCount(): Long =
        em
            .createQuery("select count(pt) from PointTransaction pt", java.lang.Long::class.java)
            .singleResult
            .toLong()

    private fun inboxCount(
        consumer: String,
        eventId: String,
    ): Long =
        db
            .sql("SELECT COUNT(*) FROM processed_message WHERE consumer = :consumer AND event_id = :eventId")
            .param("consumer", consumer)
            .param("eventId", eventId)
            .query(Long::class.java)
            .single()

    private fun pendingGroupCount(): Long =
        em
            .createQuery("select count(g) from OrderGroup g where g.status = :status", java.lang.Long::class.java)
            .setParameter("status", OrderGroupStatus.PENDING_PAYMENT)
            .singleResult
            .toLong()

    /** 복원은 컨슈머의 별도 트랜잭션에서 오므로 기다림. 값이 잠깐 맞았다 더 커지는 이중 복원까지 잡으려 잠시 유지되는지도 봄 */
    private fun awaitStock(
        combinationId: Long,
        expected: Int,
        reason: String,
    ) {
        await().during(HOLD).atMost(WAIT).untilAsserted { assertEquals(expected, stockOf(combinationId), reason) }
    }

    @Test
    fun `만료된 결제대기 그룹은 취소되고 잡아 둔 재고가 돌아온다`() {
        val buyerId = createActiveUser()
        val first = seedCombination(stock = 3)
        val second = seedCombination(stock = 0)
        val groupId = seedGroup(buyerId, orders = listOf(listOf(ItemSeed(first, 2)), listOf(ItemSeed(second, 1))))

        sweepAll()

        assertEquals(OrderGroupStatus.CANCELLED, groupStatusOf(groupId), "만료 시각이 지난 결제대기 그룹은 취소돼야 한다")
        assertEquals(
            listOf(OrderStatus.CANCELLED, OrderStatus.CANCELLED),
            orderStatusesOf(groupId),
            "그룹만 취소하고 셀러별 주문을 남겨 두면 셀러 화면에 유령 주문이 남는다",
        )
        awaitStock(first, 5, "첫 셀러 품목의 2개가 재고로 돌아와야 한다")
        awaitStock(second, 1, "둘째 셀러 품목의 1개가 재고로 돌아와야 한다")
    }

    @Test
    fun `미만료·결제완료·이미취소 그룹은 건드리지 않는다`() {
        val buyerId = createActiveUser()
        val notYet = seedCombination(stock = 3)
        val paid = seedCombination(stock = 3)
        val alreadyCancelled = seedCombination(stock = 3)

        val notYetGroup = seedGroup(buyerId, listOf(listOf(ItemSeed(notYet, 2))), expiresAt = LocalDateTime.now().plusMinutes(10))
        val paidGroup = seedGroup(buyerId, listOf(listOf(ItemSeed(paid, 2)))) { it.markPaid() }
        val cancelledGroup = seedGroup(buyerId, listOf(listOf(ItemSeed(alreadyCancelled, 2)))) { it.cancelUnpaid() }

        sweepAll()

        assertEquals(OrderGroupStatus.PENDING_PAYMENT, groupStatusOf(notYetGroup), "아직 만료 전인 그룹을 취소하면 결제 중인 주문이 사라진다")
        assertEquals(OrderGroupStatus.PAID, groupStatusOf(paidGroup), "결제가 끝난 그룹은 만료 대상이 아니다")
        assertEquals(OrderGroupStatus.CANCELLED, groupStatusOf(cancelledGroup), "이미 취소된 그룹의 상태는 그대로여야 한다")
        assertEquals(3, stockOf(notYet), "만료 전 그룹의 재고를 되돌리면 안 된다")
        assertEquals(3, stockOf(paid), "결제된 그룹의 재고를 되돌리면 안 된다")
        assertEquals(3, stockOf(alreadyCancelled), "이미 취소된 그룹의 재고를 다시 되돌리면 안 된다")
    }

    @Test
    fun `결제 준비만 하고 결제창을 열지 않은 만료 건은 취소되고 재고가 돌아온다`() {
        val buyerId = createActiveUser()
        val combination = seedCombination(stock = 3)
        val groupId = seedGroup(buyerId, orders = listOf(listOf(ItemSeed(combination, 2))))
        seedPayment(groupId) // 결제행은 있지만 포트원은 모름(404)

        sweepAll()

        assertEquals(OrderGroupStatus.CANCELLED, groupStatusOf(groupId), "결제행만 남긴 이탈을 안 치우면 만료 청소의 주된 대상이 영영 남는다")
        awaitStock(combination, 5, "결제창까지 안 간 이탈 건의 재고가 돌아와야 한다")
    }

    @Test
    fun `포트원에서 미성사로 확인된 결제 시도 건은 취소되고 재고가 돌아온다`() {
        val buyerId = createActiveUser()
        val combination = seedCombination(stock = 0)
        val groupId = seedGroup(buyerId, orders = listOf(listOf(ItemSeed(combination, 2))))
        seedPayment(groupId)
        paymentGateway.stub(publicIdOf(groupId), gatewayView(PaymentGatewayStatus.FAILED))

        sweepAll()

        assertEquals(OrderGroupStatus.CANCELLED, groupStatusOf(groupId), "돈이 안 잡힌 실패 건을 안 치우면 만료 청소가 실질적으로 돌지 않는다")
        awaitStock(combination, 2, "실패 건이 잡아 둔 재고가 돌아와야 한다")
    }

    @Test
    fun `포트원에 승인이 남은 만료 건은 건드리지 않는다`() {
        val buyerId = createActiveUser()
        val paidAtGateway = seedCombination(stock = 0)
        val behind = seedCombination(stock = 0)
        val paidGroup = seedGroup(buyerId, orders = listOf(listOf(ItemSeed(paidAtGateway, 2))))
        val behindGroup = seedGroup(buyerId, orders = listOf(listOf(ItemSeed(behind, 3))))
        seedPayment(paidGroup)
        paymentGateway.stub(publicIdOf(paidGroup), gatewayView(PaymentGatewayStatus.PAID))

        sweepAll()

        assertEquals(
            OrderGroupStatus.PENDING_PAYMENT,
            groupStatusOf(paidGroup),
            "돈이 잡힌 건을 여기서 취소하면 결제 확정 작업이 환불 근거를 잃는다",
        )
        assertEquals(OrderGroupStatus.CANCELLED, groupStatusOf(behindGroup), "건너뛴 건이 같은 청크의 뒤 건을 막으면 안 된다")
        awaitStock(behind, 3, "뒤 건의 재고는 돌아와야 한다")
        assertEquals(0, stockOf(paidAtGateway), "돈이 잡힌 건의 재고를 되돌리면 재고가 이중으로 풀린다")
    }

    @Test
    fun `두 번 연속 실행해도 재고는 한 번만 복원된다`() {
        val buyerId = createActiveUser()
        val combination = seedCombination(stock = 0)
        seedGroup(buyerId, orders = listOf(listOf(ItemSeed(combination, 2))))

        sweepAll()
        sweepAll()

        awaitStock(combination, 2, "선점 전이가 재실행 방어라 두 번째 회차는 아무것도 되돌리면 안 된다")
    }

    @Test
    fun `같은 옵션 조합이 여러 품목에 걸치면 수량을 합산해 복원한다`() {
        val buyerId = createActiveUser()
        val combination = seedCombination(stock = 0)
        seedGroup(
            buyerId,
            orders =
                listOf(
                    listOf(ItemSeed(combination, 2), ItemSeed(combination, 3)),
                    listOf(ItemSeed(combination, 4)),
                ),
        )

        sweepAll()

        awaitStock(combination, 9, "같은 조합을 하나로 접으면서 수량까지 접으면 재고가 덜 돌아온다")
    }

    @Test
    fun `적립금을 쓴 그룹이 만료되면 잔액이 돌아오고 복원 원장이 한 줄 남는다`() {
        val buyerId = createActiveUser()
        seedPointBalance(buyerId, 500L)
        val groupId = seedGroup(buyerId, usedPoint = 1_000L)
        val releaseKey = "RELEASE:ORDER:${publicIdOf(groupId)}"

        sweepAll()
        sweepAll()

        await().during(HOLD).atMost(WAIT).untilAsserted {
            assertEquals(1_500L, pointBalanceOf(buyerId), "그룹이 잡고 있던 적립금 1,000원이 잔액에 더해져야 한다")
        }
        assertEquals(
            listOf(PointTransactionType.RELEASE),
            ledgerTypesOf(releaseKey),
            "사용 원장과 짝이 되는 RELEASE 원장이 정확히 한 줄이어야 한다",
        )
    }

    @Test
    fun `적립금을 안 쓴 그룹이 만료되면 원장 없이 취소만 된다`() {
        val buyerId = createActiveUser()
        seedPointBalance(buyerId, 500L)
        val combination = seedCombination(stock = 0)
        val groupId = seedGroup(buyerId, orders = listOf(listOf(ItemSeed(combination, 2))), usedPoint = 0L)

        sweepAll()

        assertEquals(OrderGroupStatus.CANCELLED, groupStatusOf(groupId), "적립금을 안 썼어도 그룹은 취소돼야 한다")
        awaitStock(combination, 2, "적립금을 안 썼어도 재고는 돌아와야 한다")
        assertEquals(500L, pointBalanceOf(buyerId), "쓴 적이 없는 적립금이 잔액에 더해지면 안 된다")
        assertEquals(0L, ledgerCount(), "복원할 적립금이 없으면 원장도 남기지 않아야 한다")
    }

    @Test
    fun `같은 취소 메시지가 두 번 배달돼도 재고와 적립금은 한 번만 돌아온다`() {
        val buyerId = createActiveUser()
        seedPointBalance(buyerId, 500L)
        val combination = seedCombination(stock = 0)
        val message =
            OrderGroupCancelledMessage(
                orderGroupPublicId = "dup-${UUID.randomUUID()}",
                buyerId = buyerId,
                usedPoint = 1_000L,
                items = listOf(OrderGroupCancelledMessage.Item(combination, 2)),
            )

        tx.executeWithoutResult { messagePublisher.publish(message) }
        // 브로커 재전달을 흉내 냄. 아웃박스에 남은 엔벨로프 그대로를 같은 파티션 키로 한 번 더 보냄
        val envelopeJson =
            db
                .sql("SELECT payload FROM outbox_message WHERE event_id = :eventId")
                .param("eventId", message.eventId)
                .query(String::class.java)
                .single()
        kafka.send(Topics.ORDER, message.aggregateId, envelopeJson).get()

        awaitStock(combination, 2, "인박스가 없으면 재전달된 메시지가 재고를 두 번 되돌린다")
        await().during(HOLD).atMost(WAIT).untilAsserted {
            assertEquals(1_500L, pointBalanceOf(buyerId), "인박스가 없으면 재전달된 메시지가 적립금을 두 번 되돌린다")
        }
        assertEquals(1L, ledgerCount(), "같은 사건의 복원 원장은 한 줄이어야 한다")
    }

    @Test
    fun `재고 컨슈머가 실패해도 적립금 컨슈머는 영향받지 않고 실패 메시지는 DLT로 격리된다`() {
        val buyerId = createActiveUser()
        seedPointBalance(buyerId, 500L)
        val healthy = seedCombination(stock = 0)
        // 있는 조합 뒤에 없는 조합을 섞어, 앞 조합의 복원까지 함께 롤백되는지 봄
        val message =
            OrderGroupCancelledMessage(
                orderGroupPublicId = "dlt-${UUID.randomUUID()}",
                buyerId = buyerId,
                usedPoint = 1_000L,
                items =
                    listOf(
                        OrderGroupCancelledMessage.Item(healthy, 2),
                        OrderGroupCancelledMessage.Item(MISSING_COMBINATION_ID, 1),
                    ),
            )

        tx.executeWithoutResult { messagePublisher.publish(message) }

        await().atMost(WAIT).untilAsserted {
            assertEquals(1_500L, pointBalanceOf(buyerId), "그룹이 달라 재고 컨슈머가 막혀도 적립금 컨슈머는 제 길을 가야 한다")
        }
        val props =
            mapOf<String, Any>(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to brokers,
                ConsumerConfig.GROUP_ID_CONFIG to "expire-dlt-checker",
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
            )
        DefaultKafkaConsumerFactory<String, String>(props).createConsumer().use { consumer ->
            consumer.subscribe(listOf(Topics.ORDER_DLT))
            val records = KafkaTestUtils.getRecords(consumer, DLT_WAIT)
            assertTrue(
                records.records(Topics.ORDER_DLT).any { it.value().contains(message.eventId) },
                "재시도를 소진한 재고 복원 메시지는 DLT로 격리돼 사람이 리플레이할 수 있어야 한다",
            )
        }
        assertEquals(0, stockOf(healthy), "한 조합이 실패하면 같은 메시지의 다른 조합 복원도 함께 롤백돼야 리플레이가 이중 복원이 안 된다")
        assertEquals(
            0L,
            inboxCount(StockRestoreOnOrderGroupCancelledConsumer.GROUP, message.eventId),
            "실패한 처리의 인박스 기록이 남으면 DLT 리플레이가 중복으로 스킵된다",
        )
        assertEquals(
            1L,
            inboxCount(PointReleaseOnOrderGroupCancelledConsumer.GROUP, message.eventId),
            "성공한 적립금 컨슈머의 인박스 기록은 남아야 재전달을 거른다",
        )
    }

    @Test
    fun `한 건이 실패해도 같은 청크의 나머지는 처리된다`() {
        val buyerId = createActiveUser()
        val combination = seedCombination(stock = 0)
        // 포트원 조회가 실패하는 건을 심어 그 건의 판정을 막음
        val poison = seedGroup(buyerId)
        paymentGateway.failFindFor(publicIdOf(poison))
        val healthy = seedGroup(buyerId, orders = listOf(listOf(ItemSeed(combination, 2))))

        sweepAll()

        assertEquals(OrderGroupStatus.PENDING_PAYMENT, groupStatusOf(poison), "포트원 조회가 실패한 건은 취소하지 않고 다음 회차로 넘겨야 한다")
        assertEquals(OrderGroupStatus.CANCELLED, groupStatusOf(healthy), "건별로 격리하지 않으면 한 건의 실패가 같은 청크 전체를 막는다")
        awaitStock(combination, 2, "성공한 건의 재고는 돌아와야 한다")
    }

    @Test
    fun `대상이 청크보다 많으면 다음 회차까지 이어서 돈다`() {
        val buyerId = createActiveUser()
        val base = LocalDateTime.now()
        repeat(CHUNK + 1) { seedGroup(buyerId, expiresAt = base.minusMinutes(it + 1L)) }

        sweepAll()

        assertEquals(0L, pendingGroupCount(), "한 회차에서 멈추면 청크를 넘긴 건이 다음 주기까지 재고와 적립금을 잡아 둔다")
    }

    @Test
    fun `게이트웨이가 전면 장애여도 실행은 끝나고 대상은 결제대기로 남는다`() {
        val buyerId = createActiveUser()
        repeat(CHUNK) { seedGroup(buyerId) }
        paymentGateway.failAllFinds()

        // 실패 건은 결제대기로 남고, 커서가 지나가므로 실행은 반드시 끝남. 여기서 안 끝나면 핫루프 회귀
        assertTimeoutPreemptively(Duration.ofSeconds(30)) { sweepAll() }

        assertEquals(CHUNK.toLong(), pendingGroupCount(), "판정에 실패한 건은 취소하지 않고 결제대기로 남아야 한다")
    }

    @Test
    fun `만료 대상은 만료가 이른 것부터 잡힌다`() {
        val buyerId = createActiveUser()
        val base = LocalDateTime.now()
        // id 오름차순과 만료 순서를 어긋나게 심어야 정렬이 실제로 걸림
        val latest = seedGroup(buyerId, expiresAt = base.minusMinutes(1))
        val earliest = seedGroup(buyerId, expiresAt = base.minusMinutes(3))
        val middle = seedGroup(buyerId, expiresAt = base.minusMinutes(2))

        assertEquals(
            listOf(earliest, middle),
            orderGroupRepository.findExpiredPendingTargets(base, null, 2).map { it.id },
            "정렬이 없으면 가장 오래 기다린 그룹이 매 회차 청크 밖으로 밀린다 (latest=$latest)",
        )
    }

    @Test
    fun `이미 취소된 그룹은 선점 전이가 진다`() {
        val buyerId = createActiveUser()
        val groupId = seedGroup(buyerId) { it.cancelUnpaid() }

        val won = tx.execute { orderGroupRepository.cancelIfPending(groupId) }!!

        assertFalse(won, "결제대기가 아닌 그룹에도 전이가 먹으면 인스턴스 둘이 같은 재고를 두 번 되돌린다")
    }

    companion object {
        // 프로덕션 상수를 끌어 쓰면 청크 크기를 바꿔도 테스트가 따라와 조기 종료를 못 잡음
        private const val CHUNK = 50
        private const val SKIP_LIMIT = 100
        private const val MISSING_COMBINATION_ID = 999_999L

        private val WAIT: Duration = Duration.ofSeconds(15)

        /** 재시도 3회(1·2·4초)를 소진하고 DLT에 실릴 때까지 */
        private val DLT_WAIT: Duration = Duration.ofSeconds(30)

        /** 복원이 한 번 맞은 뒤 이중 복원으로 더 늘지 않는지 보는 시간 */
        private val HOLD: Duration = Duration.ofSeconds(2)
    }
}
