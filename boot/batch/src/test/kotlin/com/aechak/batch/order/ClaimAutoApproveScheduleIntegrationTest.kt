package com.aechak.batch.order

import com.aechak.application.order.claim.service.ClaimAmountCalculator
import com.aechak.application.order.claim.usecase.ClaimAutoApproveUseCase
import com.aechak.application.order.claim.usecase.ClaimRefundUseCase
import com.aechak.batch.support.BatchIntegrationTestBase
import com.aechak.domain.order.claim.Claim
import com.aechak.domain.order.claim.ClaimItem
import com.aechak.domain.order.claim.enums.ClaimStatus
import com.aechak.domain.order.claim.enums.ClaimType
import com.aechak.domain.order.claim.enums.RefundStatus
import com.aechak.domain.order.claim.enums.SellerApprovalStatus
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
import com.aechak.domain.payment.error.PaymentErrorCode
import com.aechak.domain.payment.repository.PaymentRepository
import com.aechak.domain.product.category.Category
import com.aechak.domain.product.option.OptionCombination
import com.aechak.domain.product.product.Product
import com.aechak.domain.user.point.enums.PointTransactionType
import com.aechak.domain.user.user.User
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDateTime

/**
 * 클레임 자동승인 배치 통합. 승인에서 끝나지 않고 환불 집행까지 같은 회차에서 가므로 실 MySQL이 전제다 —
 * 재고 복원과 주문그룹 전이가 조건부 원자 UPDATE다.
 * 스케줄 메서드를 직접 부른다 — 타이머는 테스트 프로파일의 비활성 cron으로 묶어 뒀다.
 */
class ClaimAutoApproveScheduleIntegrationTest : BatchIntegrationTestBase() {
    @Autowired
    private lateinit var schedule: ClaimAutoApproveSchedule

    @Autowired
    private lateinit var paymentRepository: PaymentRepository

    @Autowired
    private lateinit var claimAmountCalculator: ClaimAmountCalculator

    @Autowired
    private lateinit var autoApproveUseCase: ClaimAutoApproveUseCase

    @Autowired
    private lateinit var refundUseCase: ClaimRefundUseCase

    @Test
    fun `응답 기한이 지난 클레임은 자동승인되고 환불까지 집행된다`() {
        val fixture = seedClaimRequestedOrder()

        schedule.run()

        assertEquals(SellerApprovalStatus.APPROVED, claimApprovalOf(fixture.claimPublicId))
        assertEquals(ClaimStatus.APPROVED, claimStatusOf(fixture.claimPublicId))
        assertEquals(RefundStatus.DONE, claimRefundStatusOf(fixture.claimPublicId))
        assertNotNull(claimResolvedAtOf(fixture.claimPublicId))
        assertEquals(listOf(fixture.orderGroupPublicId), paymentGateway.cancelledPaymentIds, "PG 취소는 한 번만 나가야 한다")
        assertEquals(fixture.finalPaymentAmount, paymentGateway.cancelledAmountOf(fixture.orderGroupPublicId))
        assertEquals(PaymentStatus.CANCELLED, paymentStatusOf(fixture.orderGroupId))
        assertEquals(0L, paymentCancellableOf(fixture.orderGroupId))
        assertEquals(OrderGroupStatus.CANCELLED, groupStatusOf(fixture.orderGroupId))
        assertEquals(OrderStatus.CANCELLED, orderStatusOf(fixture.orderId))
        assertEquals(listOf(OrderItemStatus.CANCELLED), itemStatusesOf(fixture.orderId))
        assertEquals(
            1,
            historyCountOf(fixture.orderId, OrderStatus.PAID, OrderStatus.CANCELLED, TriggeredBy.SYSTEM),
            "자동승인 건의 전이 주체는 셀러가 아니라 시스템이다",
        )
        assertEquals(STOCK + QUANTITY, stockOf(fixture.optionCombinationId))
    }

    @Test
    fun `적립금을 쓴 주문도 현금과 적립금을 나눠 되돌린다`() {
        val fixture = seedClaimRequestedOrder(usedPoint = USED_POINT, pointBalance = 500L)

        schedule.run()

        assertEquals(RefundStatus.DONE, claimRefundStatusOf(fixture.claimPublicId))
        assertEquals(fixture.finalPaymentAmount, paymentGateway.cancelledAmountOf(fixture.orderGroupPublicId))
        assertEquals(500L + USED_POINT, pointBalanceOf(fixture.buyerId))
        assertEquals(1, releaseLedgerCountOf(fixture.buyerId))
        assertEquals(OrderGroupStatus.CANCELLED, groupStatusOf(fixture.orderGroupId))
    }

    @Test
    fun `응답 기한이 남은 클레임은 건드리지 않는다`() {
        val fixture = seedClaimRequestedOrder(autoApproveDueAt = LocalDateTime.now().plusDays(2))

        schedule.run()

        assertEquals(ClaimStatus.CLAIM_REQUESTED, claimStatusOf(fixture.claimPublicId))
        assertEquals(SellerApprovalStatus.REQUESTED, claimApprovalOf(fixture.claimPublicId))
        assertEquals(RefundStatus.NONE, claimRefundStatusOf(fixture.claimPublicId))
        assertEquals(emptyList<String>(), paymentGateway.cancelledPaymentIds)
        assertEquals(OrderGroupStatus.PAID, groupStatusOf(fixture.orderGroupId))
        assertEquals(STOCK, stockOf(fixture.optionCombinationId))
    }

    @Test
    fun `셀러가 이미 승인한 건은 자동승인 스캔에서 빠진다`() {
        val fixture = seedClaimRequestedOrder()
        approveBySeller(fixture.claimPublicId)

        schedule.run()

        assertEquals(
            RefundStatus.NONE,
            claimRefundStatusOf(fixture.claimPublicId),
            "셀러가 승인한 건의 집행은 환불 배치 몫이다 — 자동승인 회차가 겹쳐 집행하면 안 된다",
        )
        assertEquals(emptyList<String>(), paymentGateway.cancelledPaymentIds)
        assertEquals(OrderGroupStatus.PAID, groupStatusOf(fixture.orderGroupId))
        assertEquals(STOCK, stockOf(fixture.optionCombinationId))
    }

    @Test
    fun `셀러가 이미 거부한 건은 기한이 지나도 되살아나지 않는다`() {
        val fixture = seedClaimRequestedOrder()
        rejectBySeller(fixture.claimPublicId)

        schedule.run()

        assertEquals(ClaimStatus.REJECTED, claimStatusOf(fixture.claimPublicId))
        assertEquals(SellerApprovalStatus.REJECTED, claimApprovalOf(fixture.claimPublicId))
        assertEquals(emptyList<String>(), paymentGateway.cancelledPaymentIds)
        assertEquals(OrderGroupStatus.PAID, groupStatusOf(fixture.orderGroupId))
    }

    @Test
    fun `PG 취소가 실패하면 승인은 남고 환불만 실패로 기록된다`() {
        val fixture = seedClaimRequestedOrder()
        paymentGateway.stubCancelError(fixture.orderGroupPublicId, PaymentErrorCode.PAYMENT_GATEWAY_ERROR)

        schedule.run()

        assertEquals(SellerApprovalStatus.APPROVED, claimApprovalOf(fixture.claimPublicId), "승인은 되돌리지 않는다")
        assertEquals(ClaimStatus.REFUND_FAILED, claimStatusOf(fixture.claimPublicId))
        assertEquals(RefundStatus.REFUND_FAILED, claimRefundStatusOf(fixture.claimPublicId))
        assertNull(claimResolvedAtOf(fixture.claimPublicId))
        assertEquals(PaymentStatus.APPROVED, paymentStatusOf(fixture.orderGroupId), "취소 선점은 풀려야 한다")
        assertEquals(fixture.finalPaymentAmount, paymentCancellableOf(fixture.orderGroupId))
        assertEquals(OrderGroupStatus.PAID, groupStatusOf(fixture.orderGroupId))
        assertEquals(OrderStatus.PAID, orderStatusOf(fixture.orderId))
        assertEquals(STOCK, stockOf(fixture.optionCombinationId))
    }

    @Test
    fun `환불에 실패한 건은 다음 회차가 다시 집행하지 않는다`() {
        val fixture = seedClaimRequestedOrder()
        paymentGateway.stubCancelError(fixture.orderGroupPublicId, PaymentErrorCode.PAYMENT_GATEWAY_ERROR)
        schedule.run()

        schedule.run()

        assertEquals(RefundStatus.REFUND_FAILED, claimRefundStatusOf(fixture.claimPublicId))
        assertEquals(emptyList<String>(), paymentGateway.cancelledPaymentIds, "실패 건을 무한 재시도하면 PG에 같은 취소가 계속 나간다")
        assertEquals(STOCK, stockOf(fixture.optionCombinationId))
    }

    @Test
    fun `두 번 돌려도 환불과 자산 복원은 한 번만 일어난다`() {
        val fixture = seedClaimRequestedOrder(usedPoint = USED_POINT, pointBalance = 500L)

        schedule.run()
        schedule.run()

        assertEquals(1, paymentGateway.cancelledPaymentIds.size)
        assertEquals(fixture.finalPaymentAmount, paymentGateway.cancelledAmountOf(fixture.orderGroupPublicId))
        assertEquals(STOCK + QUANTITY, stockOf(fixture.optionCombinationId))
        assertEquals(500L + USED_POINT, pointBalanceOf(fixture.buyerId))
        assertEquals(1, releaseLedgerCountOf(fixture.buyerId))
        assertEquals(1, historyCountOf(fixture.orderId, OrderStatus.PAID, OrderStatus.CANCELLED, TriggeredBy.SYSTEM))
    }

    @Test
    fun `기한이 이른 건부터 처리하고 페이지 상한 뒤는 다음 회차가 이어 간다`() {
        val older = seedClaimRequestedOrder(autoApproveDueAt = LocalDateTime.now().minusDays(2))
        val newer = seedClaimRequestedOrder(autoApproveDueAt = LocalDateTime.now().minusDays(1))
        val oneAtATime = ClaimAutoApproveSchedule(autoApproveUseCase, refundUseCase, pageSize = 1)

        oneAtATime.run()
        assertEquals(RefundStatus.DONE, claimRefundStatusOf(older.claimPublicId), "기한이 먼저 지난 건이 앞선다")
        assertEquals(ClaimStatus.CLAIM_REQUESTED, claimStatusOf(newer.claimPublicId))

        oneAtATime.run()
        assertEquals(RefundStatus.DONE, claimRefundStatusOf(newer.claimPublicId), "다음 회차가 남은 건을 이어 받아야 한다")
    }

    // ---------- 픽스처 ----------

    private class Fixture(
        val buyerId: Long,
        val orderGroupId: Long,
        val orderGroupPublicId: String,
        val orderId: Long,
        val optionCombinationId: Long,
        val finalPaymentAmount: Long,
        val claimPublicId: String,
    )

    /** 결제완료 주문에 취소 클레임이 요청된 상태를 재현한다 — 셀러는 아직 아무것도 하지 않았다 */
    private fun seedClaimRequestedOrder(
        usedPoint: Long = 0L,
        pointBalance: Long = 0L,
        autoApproveDueAt: LocalDateTime = LocalDateTime.now().minusMinutes(1),
    ): Fixture {
        val fixture =
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
                val combination = OptionCombination.create(product, "기본 / 1개", 0L, STOCK, "sig-${System.nanoTime()}")
                em.persist(combination)

                val group =
                    OrderGroup.create(
                        buyerId = buyer.id,
                        deliveryAddressId = 1L,
                        deliveryAddress = snapshot(),
                        usedPoint = usedPoint,
                        totalProductAmount = PRODUCT_PRICE * QUANTITY,
                        totalShippingFee = SHIPPING_FEE,
                        idempotencyKey = "auto-approve-${System.nanoTime()}",
                        expiresAt = LocalDateTime.now().plusMinutes(10),
                    )
                em.persist(group)
                val order =
                    Order.create(
                        orderGroup = group,
                        sellerId = SELLER_ID,
                        sellerNameSnapshot = "스토어",
                        allocatedCouponDiscount = 0L,
                        sellerShippingFee = SHIPPING_FEE,
                        baseShippingFeeSnapshot = SHIPPING_FEE,
                        freeShippingThresholdSnapshot = null,
                        items =
                            listOf(
                                OrderItem.of(
                                    productId = product.id,
                                    optionCombinationId = combination.id,
                                    optionNameSnapshot = combination.name,
                                    quantity = QUANTITY,
                                    unitPriceSnapshot = PRODUCT_PRICE,
                                    discountAllocatedAmount = 0L,
                                    productVersionId = PRODUCT_VERSION_ID,
                                ),
                            ),
                    )
                em.persist(order)
                em.flush()
                group.markPaid()
                order.markPaid()
                em.flush()
                Fixture(
                    buyerId = buyer.id,
                    orderGroupId = group.id,
                    orderGroupPublicId = group.publicId,
                    orderId = order.id,
                    optionCombinationId = combination.id,
                    finalPaymentAmount = group.finalPaymentAmount,
                    claimPublicId = requestClaim(order, usedPoint, autoApproveDueAt),
                )
            }!!
        seedApprovedPayment(fixture)
        em.clear()
        return fixture
    }

    /** 요청 엔드포인트와 같은 금액을 claim_items에 확정 저장한다 — 집행이 저장된 값을 그대로 쓴다 */
    private fun requestClaim(
        order: Order,
        usedPoint: Long,
        autoApproveDueAt: LocalDateTime,
    ): String {
        val targets = order.items.associate { it.id to it.quantity }
        val amounts = claimAmountCalculator.calculate(order, listOf(order), usedPoint, targets)
        val claim =
            Claim.request(
                order = order,
                claimType = ClaimType.CANCEL,
                reasonCode = "CHANGE_OF_MIND",
                reasonDetail = null,
                items = order.items.map { ClaimItem.of(it, it.quantity, amounts.itemRefunds.getValue(it.id)) },
                autoApproveDueAt = autoApproveDueAt,
            )
        em.persist(claim)
        em.flush()
        return claim.publicId
    }

    /** 결제가 승인된 상태 — 결제 식별자는 주문그룹 publicId다. PG 쪽 잔액도 같이 심어야 취소가능액 대조를 통과한다 */
    private fun seedApprovedPayment(fixture: Fixture) {
        paymentGateway.preRegister(fixture.orderGroupPublicId, fixture.finalPaymentAmount)
        tx.execute {
            paymentRepository.save(
                Payment
                    .prepare(
                        orderGroupId = fixture.orderGroupId,
                        paymentId = fixture.orderGroupPublicId,
                        method = PaymentMethod.CARD,
                        targetAmount = fixture.finalPaymentAmount,
                    ).approve("tx-${fixture.orderGroupPublicId}", fixture.finalPaymentAmount),
            )
        }
    }

    private fun approveBySeller(claimPublicId: String) {
        tx.execute { claimOf(claimPublicId).approveBySeller(null) }
        em.clear()
    }

    private fun rejectBySeller(claimPublicId: String) {
        tx.execute { claimOf(claimPublicId).rejectBySeller("재고를 이미 출고했습니다") }
        em.clear()
    }

    private fun claimOf(claimPublicId: String): Claim =
        em
            .createQuery("select c from Claim c where c.publicId = :pid", Claim::class.java)
            .setParameter("pid", claimPublicId)
            .singleResult

    private fun snapshot(): DeliveryAddressSnapshot =
        DeliveryAddressSnapshot(
            receiverNameEnc = "enc-name",
            contactNumberEnc = "enc-contact",
            zipCode = "12345",
            baseAddress = "서울시 애착구 멍냥로 1",
            detailAddress = "101동 202호",
            deliveryMemo = null,
        )

    // ---------- 단언 헬퍼 ----------

    private fun claimStatusOf(claimPublicId: String): ClaimStatus =
        byPublicId("select c.status from Claim c where c.publicId = :pid", ClaimStatus::class.java, claimPublicId)

    private fun claimApprovalOf(claimPublicId: String): SellerApprovalStatus =
        byPublicId(
            "select c.sellerApprovalStatus from Claim c where c.publicId = :pid",
            SellerApprovalStatus::class.java,
            claimPublicId,
        )

    private fun claimRefundStatusOf(claimPublicId: String): RefundStatus =
        byPublicId("select c.refundStatus from Claim c where c.publicId = :pid", RefundStatus::class.java, claimPublicId)

    private fun claimResolvedAtOf(claimPublicId: String): LocalDateTime? =
        em
            .createQuery("select c.resolvedAt from Claim c where c.publicId = :pid", LocalDateTime::class.java)
            .setParameter("pid", claimPublicId)
            .resultList
            .single()

    private fun groupStatusOf(orderGroupId: Long): OrderGroupStatus =
        byId("select g.status from OrderGroup g where g.id = :id", OrderGroupStatus::class.java, orderGroupId)

    private fun orderStatusOf(orderId: Long): OrderStatus =
        byId("select o.status from Order o where o.id = :id", OrderStatus::class.java, orderId)

    private fun itemStatusesOf(orderId: Long): List<OrderItemStatus> =
        em
            .createQuery("select distinct i.itemStatus from Order o join o._items i where o.id = :id", OrderItemStatus::class.java)
            .setParameter("id", orderId)
            .resultList

    private fun stockOf(optionCombinationId: Long): Int =
        byId(
            "select oc.stockQuantity from OptionCombination oc where oc.id = :id",
            java.lang.Integer::class.java,
            optionCombinationId,
        ).toInt()

    private fun pointBalanceOf(userId: Long): Long =
        byId("select u.pointBalance from User u where u.id = :id", java.lang.Long::class.java, userId).toLong()

    private fun paymentStatusOf(orderGroupId: Long): PaymentStatus =
        requireNotNull(paymentRepository.findByOrderGroupId(orderGroupId)) { "결제 행이 있어야 합니다" }.status

    private fun paymentCancellableOf(orderGroupId: Long): Long =
        requireNotNull(paymentRepository.findByOrderGroupId(orderGroupId)?.cancellableAmount) { "승인된 결제에는 취소가능액이 있다" }

    private fun releaseLedgerCountOf(userId: Long): Int =
        em
            .createQuery(
                "select count(pt) from PointTransaction pt where pt.buyer.id = :id and pt.transactionType = :type",
                java.lang.Long::class.java,
            ).setParameter("id", userId)
            .setParameter("type", PointTransactionType.RELEASE)
            .singleResult
            .toInt()

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

    private fun <T> byId(
        jpql: String,
        type: Class<T>,
        id: Long,
    ): T = em.createQuery(jpql, type).setParameter("id", id).singleResult

    private fun <T> byPublicId(
        jpql: String,
        type: Class<T>,
        publicId: String,
    ): T = em.createQuery(jpql, type).setParameter("pid", publicId).singleResult

    private companion object {
        private const val SELLER_ID = 7L
        private const val PRODUCT_PRICE = 10_000L
        private const val SHIPPING_FEE = 3_000L
        private const val PRODUCT_VERSION_ID = 1L
        private const val STOCK = 5
        private const val QUANTITY = 2
        private const val USED_POINT = 1_000L
    }
}
