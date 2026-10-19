package com.aechak.batch.order

import com.aechak.application.order.usecase.OrderExpiryUseCase
import com.aechak.application.order.usecase.OrderUseCase
import com.aechak.application.order.usecase.command.ExpireOrderGroupCommand
import com.aechak.application.order.usecase.result.ExpireOrderGroupStatus
import com.aechak.application.payment.port.PaymentGatewayStatus
import com.aechak.application.payment.port.PaymentGatewayView
import com.aechak.application.payment.usecase.PaymentUseCase
import com.aechak.batch.support.BatchIntegrationTestBase
import com.aechak.domain.order.group.DeliveryAddressSnapshot
import com.aechak.domain.order.group.OrderGroup
import com.aechak.domain.order.group.enums.OrderGroupStatus
import com.aechak.domain.order.order.Order
import com.aechak.domain.order.order.OrderItem
import com.aechak.domain.order.order.enums.OrderItemStatus
import com.aechak.domain.order.order.enums.OrderStatus
import com.aechak.domain.order.order.enums.TriggeredBy
import com.aechak.domain.payment.Payment
import com.aechak.domain.payment.enums.PaymentMethod
import com.aechak.domain.payment.enums.PaymentStatus
import com.aechak.domain.payment.repository.PaymentRepository
import com.aechak.domain.product.category.Category
import com.aechak.domain.product.option.OptionCombination
import com.aechak.domain.product.product.Product
import com.aechak.domain.user.point.enums.PointTransactionType
import com.aechak.domain.user.user.User
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * 주문서 TTL 만료 배치 통합. 재고 복원은 조건부 원자 UPDATE라 Testcontainers 실 MySQL이 전제(H2 금지).
 * 스케줄 메서드를 직접 부른다 — 타이머는 테스트 프로파일의 먼 미래 cron으로 묶어 뒀다.
 */
class OrderExpiryScheduleIntegrationTest : BatchIntegrationTestBase() {
    @Autowired
    private lateinit var schedule: OrderExpirySchedule

    @Autowired
    private lateinit var paymentRepository: PaymentRepository

    @Autowired
    private lateinit var orderExpiryUseCase: OrderExpiryUseCase

    @Autowired
    private lateinit var paymentUseCase: PaymentUseCase

    @Autowired
    private lateinit var orderUseCase: OrderUseCase

    @Test
    fun `만료된 주문그룹은 취소되고 하위 주문도 취소 이력과 함께 취소된다`() {
        val fixture = seedExpiredOrderGroup(stock = 3, quantity = 2)

        schedule.run()

        assertEquals(OrderGroupStatus.CANCELLED, groupStatusOf(fixture.orderGroupId))
        assertEquals(listOf(OrderStatus.CANCELLED), orderStatusesOf(fixture.orderGroupId))
        assertEquals(1, historyCountOf(fixture.orderId, OrderStatus.PENDING_PAYMENT, OrderStatus.CANCELLED, TriggeredBy.SYSTEM))
    }

    @Test
    fun `만료되면 주문 품목도 취소로 내려가 리뷰 작성 자격을 잃는다`() {
        val fixture = seedExpiredOrderGroup(stock = 3, quantity = 2)

        schedule.run()

        assertEquals(listOf(OrderItemStatus.CANCELLED), itemStatusesOf(fixture.orderGroupId))
        val reviewTarget = orderUseCase.getOrderItemForReview(fixture.orderItemId, fixture.buyerId)
        assertNotNull(reviewTarget, "품목 행은 남아 있어야 한다")
        assertFalse(reviewTarget!!.isItemReviewable, "취소된 품목은 리뷰 작성 자격이 없어야 한다")
    }

    @Test
    fun `만료되면 차감된 재고가 주문 전 수량으로 돌아온다`() {
        val fixture = seedExpiredOrderGroup(stock = 3, quantity = 2)

        schedule.run()

        assertEquals(5, stockOf(fixture.optionCombinationId), "주문 전 재고는 차감 후 3 + 주문 수량 2")
    }

    @Test
    fun `만료되면 사용한 적립금이 원장 한 행과 함께 복원된다`() {
        val fixture = seedExpiredOrderGroup(stock = 3, quantity = 2, usedPoint = 1_000L, pointBalance = 500L)
        touchUserUpdatedAt(fixture.buyerId, PAST)

        schedule.run()

        assertEquals(1_500L, pointBalanceOf(fixture.buyerId))
        assertEquals(1, releaseLedgerCountOf(fixture.buyerId))
        assertEquals(1_000L, releaseLedgerAmountOf(fixture.buyerId))
        assertEquals(
            "RELEASE:ORDER:${fixture.orderGroupPublicId}",
            releaseLedgerKeyOf(fixture.buyerId),
            "복원 멱등키는 주문그룹 단위 결정적 키여야 한다",
        )
        assertTrue(userUpdatedAtOf(fixture.buyerId).isAfter(PAST), "잔액 가산 쿼리가 updated_at을 함께 갱신해야 한다")
    }

    @Test
    fun `두 번 돌려도 재고와 적립금이 두 번 늘지 않는다`() {
        val fixture = seedExpiredOrderGroup(stock = 3, quantity = 2, usedPoint = 1_000L, pointBalance = 500L)

        schedule.run()
        schedule.run()

        assertEquals(5, stockOf(fixture.optionCombinationId))
        assertEquals(1_500L, pointBalanceOf(fixture.buyerId))
        assertEquals(1, releaseLedgerCountOf(fixture.buyerId))
        assertEquals(1, historyCountOf(fixture.orderId, OrderStatus.PENDING_PAYMENT, OrderStatus.CANCELLED, TriggeredBy.SYSTEM))
    }

    @Test
    fun `적립금을 쓰지 않은 주문은 복원 원장을 만들지 않는다`() {
        val fixture = seedExpiredOrderGroup(stock = 3, quantity = 2, usedPoint = 0L, pointBalance = 500L)

        schedule.run()

        assertEquals(OrderGroupStatus.CANCELLED, groupStatusOf(fixture.orderGroupId))
        assertEquals(0, releaseLedgerCountOf(fixture.buyerId))
        assertEquals(500L, pointBalanceOf(fixture.buyerId))
    }

    @Test
    fun `만료 시각이 지나지 않은 주문그룹은 건드리지 않는다`() {
        val fixture = seedExpiredOrderGroup(stock = 3, quantity = 2, expiresAt = LocalDateTime.now().plusMinutes(10))

        schedule.run()

        assertEquals(OrderGroupStatus.PENDING_PAYMENT, groupStatusOf(fixture.orderGroupId))
        assertEquals(listOf(OrderStatus.PENDING_PAYMENT), orderStatusesOf(fixture.orderGroupId))
        assertEquals(listOf(OrderItemStatus.ORDERED), itemStatusesOf(fixture.orderGroupId))
        assertEquals(3, stockOf(fixture.optionCombinationId))
    }

    @Test
    fun `이미 결제완료된 주문그룹은 만료 대상이 아니다`() {
        val fixture = seedExpiredOrderGroup(stock = 3, quantity = 2, usedPoint = 1_000L, pointBalance = 500L)
        markGroupPaid(fixture.orderGroupId)

        schedule.run()

        assertEquals(OrderGroupStatus.PAID, groupStatusOf(fixture.orderGroupId))
        assertEquals(3, stockOf(fixture.optionCombinationId))
        assertEquals(0, releaseLedgerCountOf(fixture.buyerId))
    }

    @Test
    fun `재고 상한에 걸려 복원하지 못하면 만료 전체를 보류한다`() {
        val fixture =
            seedExpiredOrderGroup(stock = OptionCombination.MAX_STOCK_QUANTITY, quantity = 2, usedPoint = 1_000L, pointBalance = 500L)

        schedule.run()

        assertEquals(
            OrderGroupStatus.PENDING_PAYMENT,
            groupStatusOf(fixture.orderGroupId),
            "재고를 되돌리지 못한 채 취소를 확정하면 복원 의무가 사라진다",
        )
        assertEquals(OptionCombination.MAX_STOCK_QUANTITY, stockOf(fixture.optionCombinationId))
        assertEquals(listOf(OrderItemStatus.ORDERED), itemStatusesOf(fixture.orderGroupId), "보류된 건의 품목 상태도 되돌아가야 한다")
        assertEquals(0, releaseLedgerCountOf(fixture.buyerId))
        assertEquals(0, historyCountOf(fixture.orderId, OrderStatus.PENDING_PAYMENT, OrderStatus.CANCELLED, TriggeredBy.SYSTEM))
    }

    @Test
    fun `되돌릴 옵션조합이 사라졌으면 취소를 확정한다`() {
        val fixture = seedExpiredOrderGroup(stock = 3, quantity = 2, usedPoint = 1_000L, pointBalance = 500L)
        deleteOptionCombination(fixture.optionCombinationId)

        schedule.run()

        assertEquals(OrderGroupStatus.CANCELLED, groupStatusOf(fixture.orderGroupId), "되돌릴 대상이 없는 건은 재시도해도 달라지지 않는다")
        assertEquals(1, releaseLedgerCountOf(fixture.buyerId), "적립금은 되돌릴 수 있으므로 복원한다")
    }

    @Test
    fun `결제가 승인된 주문그룹은 만료되지 않고 결제완료로 확정된다`() {
        val fixture = seedExpiredOrderGroup(stock = 3, quantity = 2, usedPoint = 1_000L, pointBalance = 500L)
        seedPayment(fixture)
        stubGateway(fixture, PaymentGatewayStatus.PAID)

        schedule.run()

        assertEquals(OrderGroupStatus.PAID, groupStatusOf(fixture.orderGroupId), "돈이 나간 그룹을 취소해선 안 된다")
        assertEquals(listOf(OrderStatus.PAID), orderStatusesOf(fixture.orderGroupId))
        assertEquals(PaymentStatus.APPROVED, paymentStatusOf(fixture.orderGroupId))
        assertEquals(3, stockOf(fixture.optionCombinationId), "재고가 복원되면 결제된 주문의 재고가 두 번 팔린다")
        assertEquals(0, releaseLedgerCountOf(fixture.buyerId))
        assertEquals(500L, pointBalanceOf(fixture.buyerId))
    }

    @Test
    fun `결제 승인 대기 중인 주문그룹은 만료되지 않는다`() {
        val fixture = seedExpiredOrderGroup(stock = 3, quantity = 2, usedPoint = 1_000L, pointBalance = 500L)
        seedPayment(fixture)
        stubGateway(fixture, PaymentGatewayStatus.PAY_PENDING)

        schedule.run()

        assertEquals(OrderGroupStatus.PENDING_PAYMENT, groupStatusOf(fixture.orderGroupId))
        assertEquals(3, stockOf(fixture.optionCombinationId))
        assertEquals(0, releaseLedgerCountOf(fixture.buyerId))
    }

    @Test
    fun `결제가 실패로 확정된 주문그룹은 만료된다`() {
        val fixture = seedExpiredOrderGroup(stock = 3, quantity = 2, usedPoint = 1_000L, pointBalance = 500L)
        seedPayment(fixture)
        stubGateway(fixture, PaymentGatewayStatus.FAILED)

        schedule.run()

        assertEquals(OrderGroupStatus.CANCELLED, groupStatusOf(fixture.orderGroupId))
        assertEquals(PaymentStatus.FAILED, paymentStatusOf(fixture.orderGroupId))
        assertEquals(5, stockOf(fixture.optionCombinationId))
        assertEquals(1, releaseLedgerCountOf(fixture.buyerId))
    }

    @Test
    fun `스캔을 거치지 않고 같은 그룹에 만료를 두 번 걸어도 복원은 한 번만 일어난다`() {
        val fixture = seedExpiredOrderGroup(stock = 3, quantity = 2, usedPoint = 1_000L, pointBalance = 500L)
        val command = ExpireOrderGroupCommand(fixture.orderGroupId)

        val first = orderExpiryUseCase.expireOrderGroup(command)
        val second = orderExpiryUseCase.expireOrderGroup(command)

        assertEquals(ExpireOrderGroupStatus.EXPIRED, first.status)
        assertEquals(ExpireOrderGroupStatus.ALREADY_SETTLED, second.status, "앞 회차가 정한 상태를 다시 처리하면 안 된다")
        assertEquals(5, stockOf(fixture.optionCombinationId))
        assertEquals(1_500L, pointBalanceOf(fixture.buyerId))
        assertEquals(1, releaseLedgerCountOf(fixture.buyerId))
    }

    /**
     * 두 요청이 같은 상태를 읽는 지점까지는 붙들 수 없어 겹친 호출의 결과만 고정한다 —
     * 조건부 전이를 지운 구현이 이 테스트를 통과할 수는 있다(상태 사전 검사가 가린다).
     */
    @Test
    fun `동시에 만료를 걸어도 재고와 적립금은 한 번만 늘어난다`() {
        val fixture = seedExpiredOrderGroup(stock = 3, quantity = 2, usedPoint = 1_000L, pointBalance = 500L)
        val command = ExpireOrderGroupCommand(fixture.orderGroupId)
        val expired = AtomicInteger()
        val start = CountDownLatch(1)
        val done = CountDownLatch(2)
        val pool = Executors.newFixedThreadPool(2)

        repeat(2) {
            pool.submit {
                start.await()
                runCatching { orderExpiryUseCase.expireOrderGroup(command) }
                    .onSuccess { if (it.status == ExpireOrderGroupStatus.EXPIRED) expired.incrementAndGet() }
                done.countDown()
            }
        }
        start.countDown()
        assertTrue(done.await(10, TimeUnit.SECONDS), "두 호출이 끝나야 한다")
        pool.shutdown()

        assertEquals(1, expired.get(), "만료를 확정하는 쪽은 하나여야 한다")
        assertEquals(5, stockOf(fixture.optionCombinationId))
        assertEquals(1_500L, pointBalanceOf(fixture.buyerId))
        assertEquals(1, releaseLedgerCountOf(fixture.buyerId))
    }

    @Test
    fun `적립금 복원이 터지면 취소 전이와 재고 복원까지 함께 롤백된다`() {
        val fixture = seedExpiredOrderGroup(stock = 3, quantity = 2, usedPoint = 1_000L, pointBalance = 500L)
        deleteUser(fixture.buyerId)

        schedule.run()

        assertEquals(OrderGroupStatus.PENDING_PAYMENT, groupStatusOf(fixture.orderGroupId))
        assertEquals(listOf(OrderStatus.PENDING_PAYMENT), orderStatusesOf(fixture.orderGroupId))
        assertEquals(3, stockOf(fixture.optionCombinationId), "적립금 복원이 터지면 앞서 늘린 재고도 돌아가야 한다")
        assertEquals(listOf(OrderItemStatus.ORDERED), itemStatusesOf(fixture.orderGroupId), "품목 상태도 함께 돌아가야 한다")
        assertEquals(0, historyCountOf(fixture.orderId, OrderStatus.PENDING_PAYMENT, OrderStatus.CANCELLED, TriggeredBy.SYSTEM))
    }

    @Test
    fun `같은 옵션을 여러 주문이 나눠 가지면 합산해 되돌리고 이미 취소된 주문은 제외한다`() {
        val fixture = seedExpiredOrderGroup(stock = 1, quantity = 2)
        val secondOrderId = addOrder(fixture, sellerId = 8L, quantity = 3)
        val cancelledOrderId = addOrder(fixture, sellerId = 9L, quantity = 4)
        cancelOrder(cancelledOrderId)

        schedule.run()

        assertEquals(6, stockOf(fixture.optionCombinationId), "결제대기 두 주문의 2 + 3만 되돌아와야 한다")
        assertEquals(1, historyCountOf(secondOrderId, OrderStatus.PENDING_PAYMENT, OrderStatus.CANCELLED, TriggeredBy.SYSTEM))
        assertEquals(0, historyCountOf(cancelledOrderId, OrderStatus.PENDING_PAYMENT, OrderStatus.CANCELLED, TriggeredBy.SYSTEM))
    }

    @Test
    fun `결제대기 주문에 이미 취소된 품목이 섞여 있으면 그 수량은 되돌리지 않는다`() {
        val fixture = seedExpiredOrderGroup(stock = 1, quantity = 2)
        val mixedOrderId = addOrder(fixture, sellerId = 8L, quantities = listOf(3, 4))
        markItemCancelled(itemIdOf(mixedOrderId, quantity = 3))

        schedule.run()

        assertEquals(OrderGroupStatus.CANCELLED, groupStatusOf(fixture.orderGroupId))
        assertEquals(7, stockOf(fixture.optionCombinationId), "선취소된 3은 빼고 2 + 4만 되돌아와야 한다")
        assertEquals(1, historyCountOf(mixedOrderId, OrderStatus.PENDING_PAYMENT, OrderStatus.CANCELLED, TriggeredBy.SYSTEM))
    }

    @Test
    fun `페이지 상한에 걸려도 다음 회차가 커서 뒤부터 이어 간다`() {
        val stuck = seedExpiredOrderGroup(stock = 3, quantity = 2, expiresAt = LocalDateTime.now().minusMinutes(5))
        seedPayment(stuck)
        stubGateway(stuck, PaymentGatewayStatus.PAY_PENDING)
        val next = seedExpiredOrderGroup(stock = 3, quantity = 2, expiresAt = LocalDateTime.now().minusMinutes(4))
        // 한 회차가 1건·1페이지만 보는 스케줄 — 회차를 넘기지 않으면 뒤 건에 닿을 수 없다
        val oneAtATime = OrderExpirySchedule(orderExpiryUseCase, paymentUseCase, pageSize = 1, maxPages = 1)

        oneAtATime.run()
        assertEquals(
            OrderGroupStatus.PENDING_PAYMENT,
            groupStatusOf(next.orderGroupId),
            "첫 회차는 승인 대기 건만 보고 상한에 걸린다",
        )

        oneAtATime.run()
        assertEquals(OrderGroupStatus.PENDING_PAYMENT, groupStatusOf(stuck.orderGroupId))
        assertEquals(OrderGroupStatus.CANCELLED, groupStatusOf(next.orderGroupId), "둘째 회차는 커서 뒤부터 이어 가야 한다")
    }

    @Test
    fun `처리하지 못한 건이 페이지를 채워도 뒤의 만료 주문이 가려지지 않는다`() {
        val stuck = seedExpiredOrderGroup(stock = 3, quantity = 2, expiresAt = LocalDateTime.now().minusMinutes(5))
        seedPayment(stuck)
        stubGateway(stuck, PaymentGatewayStatus.PAY_PENDING)
        val next = seedExpiredOrderGroup(stock = 3, quantity = 2, expiresAt = LocalDateTime.now().minusMinutes(4))

        schedule.run()

        assertEquals(OrderGroupStatus.PENDING_PAYMENT, groupStatusOf(stuck.orderGroupId), "승인 대기 건은 그대로 남는다")
        assertEquals(OrderGroupStatus.CANCELLED, groupStatusOf(next.orderGroupId), "앞자리가 막혀도 뒤 건은 처리돼야 한다")
        assertEquals(5, stockOf(next.optionCombinationId))
    }

    // ---------- 픽스처 ----------

    private class Fixture(
        val buyerId: Long,
        val orderGroupId: Long,
        val orderGroupPublicId: String,
        val orderId: Long,
        val orderItemId: Long,
        val optionCombinationId: Long,
        val finalPaymentAmount: Long,
    )

    /** 주문그룹 생성 직후 상태(재고·적립금이 이미 깎인 상태)를 재현한다 */
    private fun seedExpiredOrderGroup(
        stock: Int,
        quantity: Int,
        usedPoint: Long = 0L,
        pointBalance: Long = 0L,
        expiresAt: LocalDateTime = LocalDateTime.now().minusMinutes(1),
    ): Fixture =
        tx.execute {
            val buyer = User.preRegister()
            em.persist(buyer)
            em.flush()
            if (pointBalance > 0) {
                em
                    .createQuery("update User u set u.pointBalance = :balance where u.id = :id")
                    .setParameter("balance", pointBalance)
                    .setParameter("id", buyer.id)
                    .executeUpdate()
            }
            val category = Category.create(null, 1, "카테고리", null, 1)
            em.persist(category)
            val product = Product.register(category, SELLER_ID, "상품", null, null, PRODUCT_PRICE, null, null, null)
            em.persist(product)
            val combination = OptionCombination.create(product, "기본 / 1개", 0L, stock, "sig")
            em.persist(combination)

            val group =
                OrderGroup.create(
                    buyerId = buyer.id,
                    deliveryAddressId = 1L,
                    deliveryAddress = snapshot(),
                    usedPoint = usedPoint,
                    totalProductAmount = PRODUCT_PRICE * quantity,
                    totalShippingFee = SHIPPING_FEE,
                    idempotencyKey = "key-${System.nanoTime()}",
                    expiresAt = expiresAt,
                )
            em.persist(group)
            val order =
                Order.create(
                    orderGroup = group,
                    sellerId = SELLER_ID,
                    sellerNameSnapshot = "스토어",
                    allocatedCouponDiscount = 0,
                    sellerShippingFee = SHIPPING_FEE,
                    items =
                        listOf(
                            OrderItem.of(
                                productId = product.id,
                                optionCombinationId = combination.id,
                                optionNameSnapshot = combination.name,
                                quantity = quantity,
                                unitPriceSnapshot = PRODUCT_PRICE,
                                discountAllocatedAmount = 0,
                                productVersionId = PRODUCT_VERSION_ID,
                            ),
                        ),
                )
            em.persist(order)
            em.flush()
            Fixture(
                buyerId = buyer.id,
                orderGroupId = group.id,
                orderGroupPublicId = group.publicId,
                orderId = order.id,
                orderItemId = order.items.single().id,
                optionCombinationId = combination.id,
                finalPaymentAmount = group.finalPaymentAmount,
            )
        }!!

    /** 같은 주문그룹에 셀러 주문을 하나 더 붙인다 — 같은 옵션조합을 나눠 가진 상태를 만든다 */
    private fun addOrder(
        fixture: Fixture,
        sellerId: Long,
        quantity: Int,
    ): Long = addOrder(fixture, sellerId, listOf(quantity))

    /** 품목 여러 개를 가진 셀러 주문을 붙인다 — 수량으로 품목을 식별한다 */
    private fun addOrder(
        fixture: Fixture,
        sellerId: Long,
        quantities: List<Int>,
    ): Long =
        tx.execute {
            val group = em.find(OrderGroup::class.java, fixture.orderGroupId)
            val order =
                Order.create(
                    orderGroup = group,
                    sellerId = sellerId,
                    sellerNameSnapshot = "스토어-$sellerId",
                    allocatedCouponDiscount = 0,
                    sellerShippingFee = SHIPPING_FEE,
                    items =
                        quantities.map { quantity ->
                            OrderItem.of(
                                productId = PRODUCT_ID_UNUSED,
                                optionCombinationId = fixture.optionCombinationId,
                                optionNameSnapshot = "기본 / 1개",
                                quantity = quantity,
                                unitPriceSnapshot = PRODUCT_PRICE,
                                discountAllocatedAmount = 0,
                                productVersionId = PRODUCT_VERSION_ID,
                            )
                        },
                )
            em.persist(order)
            em.flush()
            order.id
        }!!

    private fun cancelOrder(orderId: Long) {
        tx.execute {
            em
                .createQuery("update Order o set o.status = :status where o.id = :id")
                .setParameter("status", OrderStatus.CANCELLED)
                .setParameter("id", orderId)
                .executeUpdate()
        }
    }

    /** 결제대기 주문에 이미 취소된 품목이 섞인 상태를 만든다 */
    private fun markItemCancelled(orderItemId: Long) {
        tx.execute {
            em
                .createQuery("update OrderItem oi set oi.itemStatus = :status where oi.id = :id")
                .setParameter("status", OrderItemStatus.CANCELLED)
                .setParameter("id", orderItemId)
                .executeUpdate()
        }
    }

    /** 되돌릴 옵션조합이 사라진 상태를 만든다 — order_items는 값 참조라 행이 남는다 */
    private fun deleteOptionCombination(optionCombinationId: Long) {
        tx.execute {
            em
                .createQuery("delete from OptionCombination oc where oc.id = :id")
                .setParameter("id", optionCombinationId)
                .executeUpdate()
        }
    }

    /** 적립금 복원 단계에서 터지게 만든다 — 복원 대상 사용자가 없으면 원장을 만들 수 없다 */
    private fun deleteUser(userId: Long) {
        tx.execute {
            em
                .createQuery("delete from User u where u.id = :id")
                .setParameter("id", userId)
                .executeUpdate()
        }
    }

    private fun snapshot(): DeliveryAddressSnapshot =
        DeliveryAddressSnapshot(
            receiverNameEnc = "enc-name",
            contactNumberEnc = "enc-contact",
            zipCode = "12345",
            baseAddress = "서울시 애착구 멍냥로 1",
            detailAddress = "101동 202호",
            deliveryMemo = null,
        )

    /** 결제창까지 간 상태 — 결제 식별자는 주문그룹 publicId다 */
    private fun seedPayment(fixture: Fixture) {
        tx.execute {
            paymentRepository.save(
                Payment.prepare(
                    orderGroupId = fixture.orderGroupId,
                    paymentId = fixture.orderGroupPublicId,
                    method = PaymentMethod.CARD,
                    targetAmount = fixture.finalPaymentAmount,
                ),
            )
        }
    }

    private fun stubGateway(
        fixture: Fixture,
        status: PaymentGatewayStatus,
    ) {
        paymentGateway.stub(
            fixture.orderGroupPublicId,
            PaymentGatewayView(
                status = status,
                totalAmount = fixture.finalPaymentAmount,
                paidAmount = fixture.finalPaymentAmount,
                pgTxId = "tx-${fixture.orderGroupPublicId}",
                paidAt = if (status == PaymentGatewayStatus.PAID) LocalDateTime.now() else null,
                failureCode = if (status == PaymentGatewayStatus.FAILED) "CARD_DECLINED" else null,
            ),
        )
    }

    private fun markGroupPaid(orderGroupId: Long) {
        tx.execute {
            em
                .createQuery("update OrderGroup g set g.status = :status where g.id = :id")
                .setParameter("status", OrderGroupStatus.PAID)
                .setParameter("id", orderGroupId)
                .executeUpdate()
        }
    }

    private fun touchUserUpdatedAt(
        userId: Long,
        at: LocalDateTime,
    ) {
        tx.execute {
            em
                .createQuery("update User u set u.updatedAt = :at where u.id = :id")
                .setParameter("at", at)
                .setParameter("id", userId)
                .executeUpdate()
        }
    }

    // ---------- 단언 헬퍼 ----------

    private fun groupStatusOf(orderGroupId: Long): OrderGroupStatus =
        single("select g.status from OrderGroup g where g.id = :id", OrderGroupStatus::class.java, orderGroupId)

    private fun orderStatusesOf(orderGroupId: Long): List<OrderStatus> =
        em
            .createQuery("select o.status from Order o where o.orderGroup.id = :id", OrderStatus::class.java)
            .setParameter("id", orderGroupId)
            .resultList

    private fun itemIdOf(
        orderId: Long,
        quantity: Int,
    ): Long =
        em
            .createQuery(
                "select oi.id from Order o join o._items oi where o.id = :id and oi.quantity = :quantity",
                java.lang.Long::class.java,
            ).setParameter("id", orderId)
            .setParameter("quantity", quantity)
            .singleResult
            .toLong()

    private fun itemStatusesOf(orderGroupId: Long): List<OrderItemStatus> =
        em
            .createQuery(
                "select oi.itemStatus from Order o join o._items oi where o.orderGroup.id = :id",
                OrderItemStatus::class.java,
            ).setParameter("id", orderGroupId)
            .resultList

    private fun stockOf(optionCombinationId: Long): Int =
        single(
            "select oc.stockQuantity from OptionCombination oc where oc.id = :id",
            java.lang.Integer::class.java,
            optionCombinationId,
        ).toInt()

    private fun pointBalanceOf(userId: Long): Long =
        single("select u.pointBalance from User u where u.id = :id", java.lang.Long::class.java, userId).toLong()

    private fun userUpdatedAtOf(userId: Long): LocalDateTime =
        single("select u.updatedAt from User u where u.id = :id", LocalDateTime::class.java, userId)

    private fun paymentStatusOf(orderGroupId: Long): PaymentStatus =
        requireNotNull(paymentRepository.findByOrderGroupId(orderGroupId)) { "결제 행이 있어야 합니다" }.status

    private fun releaseLedgerCountOf(userId: Long): Int = releaseLedger(userId, "count(pt)", java.lang.Long::class.java).toInt()

    private fun releaseLedgerAmountOf(userId: Long): Long = releaseLedger(userId, "pt.amount", java.lang.Long::class.java).toLong()

    private fun releaseLedgerKeyOf(userId: Long): String = releaseLedger(userId, "pt.idempotencyKey", String::class.java)

    private fun <T> releaseLedger(
        userId: Long,
        projection: String,
        type: Class<T>,
    ): T =
        em
            .createQuery(
                "select $projection from PointTransaction pt " +
                    "where pt.buyer.id = :id and pt.transactionType = :type",
                type,
            ).setParameter("id", userId)
            .setParameter("type", PointTransactionType.RELEASE)
            .singleResult

    private fun historyCountOf(
        orderId: Long,
        from: OrderStatus,
        to: OrderStatus,
        triggeredBy: TriggeredBy,
    ): Int =
        em
            .createQuery(
                "select count(h) from OrderStatusHistory h " +
                    "where h.order.id = :orderId and h.fromStatus = :from and h.toStatus = :to and h.triggeredBy = :by",
                java.lang.Long::class.java,
            ).setParameter("orderId", orderId)
            .setParameter("from", from)
            .setParameter("to", to)
            .setParameter("by", triggeredBy)
            .singleResult
            .toInt()

    private fun <T> single(
        jpql: String,
        type: Class<T>,
        id: Long,
    ): T = em.createQuery(jpql, type).setParameter("id", id).singleResult

    private companion object {
        private const val SELLER_ID = 7L
        private const val PRODUCT_PRICE = 10_000L
        private const val SHIPPING_FEE = 3_000L
        private const val PRODUCT_VERSION_ID = 1L

        /** 추가 주문의 품목에는 상품 식별자를 쓰지 않는다 — 복원 기준은 옵션조합이다 */
        private const val PRODUCT_ID_UNUSED = 1L
        private val PAST = LocalDateTime.of(2026, 1, 1, 0, 0)
    }
}
