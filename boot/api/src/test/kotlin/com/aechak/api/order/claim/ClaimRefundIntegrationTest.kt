package com.aechak.api.order.claim

import com.aechak.api.support.FakePaymentGateway
import com.aechak.api.support.IntegrationTestBase
import com.aechak.application.order.claim.usecase.ClaimRefundUseCase
import com.aechak.application.order.claim.usecase.command.ExecuteClaimRefundCommand
import com.aechak.application.order.claim.usecase.result.ExecuteClaimRefundStatus
import com.aechak.application.payment.port.PaymentCancelStatus
import com.aechak.common.error.BusinessException
import com.aechak.domain.order.claim.Claim
import com.aechak.domain.order.claim.enums.ClaimStatus
import com.aechak.domain.order.claim.enums.RefundStatus
import com.aechak.domain.order.error.OrderErrorCode
import com.aechak.domain.order.group.DeliveryAddressSnapshot
import com.aechak.domain.order.group.OrderGroup
import com.aechak.domain.order.group.enums.OrderGroupStatus
import com.aechak.domain.order.order.Order
import com.aechak.domain.order.order.OrderItem
import com.aechak.domain.order.order.enums.OrderItemStatus
import com.aechak.domain.order.order.enums.OrderStatus
import com.aechak.domain.payment.error.PaymentErrorCode
import com.aechak.domain.product.category.Category
import com.aechak.domain.product.option.OptionCombination
import com.aechak.domain.product.product.Product
import com.aechak.domain.product.product.enums.SaleStatus
import com.aechak.domain.product.version.ProductVersion
import com.aechak.domain.product.version.enums.VersionChangedBy
import com.aechak.domain.seller.seller.Seller
import com.jayway.jsonpath.JsonPath
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.web.FilterChainProxy
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.time.LocalDateTime

/**
 * 클레임 환불 집행 통합. 이 경로가 실제로 돈을 움직이므로 고정하는 것이 셋이다 —
 * 선점 1행이 유일한 멱등 게이트라는 것, 원장 차감과 주문그룹 전이가 한 트랜잭션이라는 것,
 * PG가 확정하지 않은 금액은 원장에 닿지 않는다는 것.
 * 집행 트리거는 엔드포인트가 아니라 배치라서 내부 포트를 직접 부른다.
 */
class ClaimRefundIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var context: WebApplicationContext

    @Autowired
    private lateinit var securityFilterChain: FilterChainProxy

    @Autowired
    private lateinit var paymentGateway: FakePaymentGateway

    @Autowired
    private lateinit var claimRefundUseCase: ClaimRefundUseCase

    private lateinit var mockMvc: MockMvc
    private var buyerId = 0L
    private var sellerUserId = 0L
    private lateinit var token: String
    private var sig = 0

    @BeforeEach
    fun setUp() {
        mockMvc =
            MockMvcBuilders
                .webAppContextSetup(context)
                .addFilters<DefaultMockMvcBuilder>(securityFilterChain)
                .build()
        paymentGateway.clear()
        buyerId = createActiveUser()
        sellerUserId = createActiveUser()
        token = mintAccessToken(buyerId)
    }

    @Test
    fun `전액 환불은 원장과 주문그룹과 자산을 한 번에 되돌린다`() {
        val seeded = seedPaidOrder(listOf(ItemSpec(10_000L, 2)))
        val claimPublicId = approvedClaim(seeded)

        val result = claimRefundUseCase.executeRefund(ExecuteClaimRefundCommand(claimPublicId))

        assertEquals(ExecuteClaimRefundStatus.REFUNDED, result.status)
        assertEquals(20_000L, result.refundAmount)
        assertEquals(1, paymentGateway.cancelCommands.size, "PG 취소는 한 번만 나가야 한다")
        val payment = paymentRow(seeded.orderGroupPublicId)
        assertEquals("CANCELLED", payment.status)
        assertEquals(0L, payment.cancellableAmount)
        assertEquals(20_000L, payment.cancelledAmount)
        assertEquals(20_000L, payment.targetAmount, "원승인액을 깎으면 대사가 모든 취소 건을 금액 불일치로 떨어뜨린다")
        assertEquals(20_000L, payment.realPaidAmount, "실결제액도 취소로 줄지 않는다")
        assertEquals(OrderGroupStatus.CANCELLED, groupStatusOf(seeded.orderGroupPublicId))
        assertEquals(OrderStatus.CANCELLED, orderStatusOf(seeded.orderId))
        assertEquals(listOf(OrderItemStatus.CANCELLED), itemStatusesOf(seeded.orderId))
        assertEquals(1, historyCount(seeded.orderId, OrderStatus.PAID, OrderStatus.CANCELLED))
        assertEquals(INITIAL_STOCK + 2, stockOf(seeded.optionCombinationIds.single()))
        val claim = claimOf(claimPublicId)
        assertEquals(RefundStatus.DONE, claim.refundStatus)
        assertEquals(ClaimStatus.APPROVED, claim.status)
        assertNotNull(claim.resolvedAt)
    }

    @Test
    fun `부분 환불은 그룹을 부분취소로 두고 남은 취소가능액을 정확히 남긴다`() {
        val seeded = seedPaidOrder(listOf(ItemSpec(10_000L, 1), ItemSpec(3_000L, 2)))
        val claimPublicId = approvedClaim(seeded, targets = mapOf(seeded.orderItemIds[0] to 1))

        val result = claimRefundUseCase.executeRefund(ExecuteClaimRefundCommand(claimPublicId))

        assertEquals(10_000L, result.refundAmount)
        val payment = paymentRow(seeded.orderGroupPublicId)
        assertEquals("PARTIAL_CANCELLED", payment.status)
        assertEquals(6_000L, payment.cancellableAmount, "남은 품목 금액이 그대로 취소가능액으로 남아야 한다")
        assertEquals(10_000L, payment.cancelledAmount)
        assertEquals(OrderGroupStatus.PARTIALLY_CANCELLED, groupStatusOf(seeded.orderGroupPublicId))
        assertEquals(OrderStatus.PAID, orderStatusOf(seeded.orderId), "살아 있는 품목이 있으면 주문은 취소되지 않는다")
        assertEquals(0, historyCount(seeded.orderId, OrderStatus.PAID, OrderStatus.CANCELLED))
        assertEquals(INITIAL_STOCK + 1, stockOf(seeded.optionCombinationIds[0]))
        assertEquals(INITIAL_STOCK, stockOf(seeded.optionCombinationIds[1]), "취소하지 않은 품목의 재고는 그대로다")
        assertEquals(RefundStatus.DONE, claimOf(claimPublicId).refundStatus)
    }

    @Test
    fun `부분 환불을 두 번 받으면 두 번째가 주문과 그룹을 전액취소로 종결한다`() {
        val seeded = seedPaidOrder(listOf(ItemSpec(10_000L, 1), ItemSpec(3_000L, 2)))
        val first = approvedClaim(seeded, targets = mapOf(seeded.orderItemIds[0] to 1))
        claimRefundUseCase.executeRefund(ExecuteClaimRefundCommand(first))

        val second = approvedClaim(seeded, targets = mapOf(seeded.orderItemIds[1] to 2))
        val result = claimRefundUseCase.executeRefund(ExecuteClaimRefundCommand(second))

        assertEquals(6_000L, result.refundAmount)
        assertEquals(2, paymentGateway.cancelCommands.size)
        val payment = paymentRow(seeded.orderGroupPublicId)
        assertEquals("CANCELLED", payment.status)
        assertEquals(0L, payment.cancellableAmount)
        assertEquals(16_000L, payment.cancelledAmount)
        assertEquals(OrderGroupStatus.CANCELLED, groupStatusOf(seeded.orderGroupPublicId))
        assertEquals(OrderStatus.CANCELLED, orderStatusOf(seeded.orderId))
        assertEquals(INITIAL_STOCK + 1, stockOf(seeded.optionCombinationIds[0]))
        assertEquals(INITIAL_STOCK + 2, stockOf(seeded.optionCombinationIds[1]))
    }

    @Test
    fun `적립금을 쓴 주문의 전액 환불은 현금과 적립금을 갈라 되돌린다`() {
        val seeded = seedPaidOrder(listOf(ItemSpec(10_000L, 1)), shippingFee = 3_000L, usedPoint = 4_000L)
        val claimPublicId = approvedClaim(seeded)

        val result = claimRefundUseCase.executeRefund(ExecuteClaimRefundCommand(claimPublicId))

        assertEquals(9_000L, result.refundAmount, "현금 환불액은 결제액과 같아야 한다")
        assertEquals(4_000L, result.releasedPoint)
        assertEquals(0L, paymentRow(seeded.orderGroupPublicId).cancellableAmount)
        assertEquals(4_000L, pointBalanceOf(buyerId))
        assertEquals(1, pointLedgerCount("RELEASE:CLAIM:$claimPublicId"))
    }

    @Test
    fun `부분 환불 두 건은 클레임 단위 멱등키로 적립금 원장을 따로 남긴다`() {
        val seeded = seedPaidOrder(listOf(ItemSpec(10_000L, 1), ItemSpec(10_000L, 1)), usedPoint = 4_000L)
        val first = approvedClaim(seeded, targets = mapOf(seeded.orderItemIds[0] to 1))
        val second = approvedClaim(seeded, targets = mapOf(seeded.orderItemIds[1] to 1))

        val firstResult = claimRefundUseCase.executeRefund(ExecuteClaimRefundCommand(first))
        val secondResult = claimRefundUseCase.executeRefund(ExecuteClaimRefundCommand(second))

        assertEquals(2_000L, firstResult.releasedPoint)
        assertEquals(2_000L, secondResult.releasedPoint, "그룹 단위 키를 쓰면 두 번째 복원이 조용히 사라진다")
        assertEquals(4_000L, pointBalanceOf(buyerId))
        assertEquals(1, pointLedgerCount("RELEASE:CLAIM:$first"))
        assertEquals(1, pointLedgerCount("RELEASE:CLAIM:$second"))
    }

    @Test
    fun `두 번 집행해도 PG 취소와 재고 적립금은 한 번만 움직인다`() {
        val seeded = seedPaidOrder(listOf(ItemSpec(10_000L, 2)), usedPoint = 4_000L)
        val claimPublicId = approvedClaim(seeded)
        claimRefundUseCase.executeRefund(ExecuteClaimRefundCommand(claimPublicId))

        val second = claimRefundUseCase.executeRefund(ExecuteClaimRefundCommand(claimPublicId))

        assertEquals(ExecuteClaimRefundStatus.SKIPPED, second.status)
        assertEquals(1, paymentGateway.cancelCommands.size)
        assertEquals(INITIAL_STOCK + 2, stockOf(seeded.optionCombinationIds.single()))
        assertEquals(4_000L, pointBalanceOf(buyerId))
        assertEquals(1, pointLedgerCount("RELEASE:CLAIM:$claimPublicId"))
    }

    @Test
    fun `PG가 취소를 거절하면 환불 실패로 남고 원장 그룹 자산 모두 그대로다`() {
        val seeded = seedPaidOrder(listOf(ItemSpec(10_000L, 2)), usedPoint = 4_000L)
        val claimPublicId = approvedClaim(seeded)
        paymentGateway.stubCancelStatus(seeded.orderGroupPublicId, PaymentCancelStatus.FAILED)

        val e =
            assertThrows(BusinessException::class.java) {
                claimRefundUseCase.executeRefund(ExecuteClaimRefundCommand(claimPublicId))
            }

        assertEquals(OrderErrorCode.CLAIM_REFUND_FAILED, e.errorCode)
        val claim = claimOf(claimPublicId)
        assertEquals(ClaimStatus.REFUND_FAILED, claim.status)
        assertEquals(RefundStatus.REFUND_FAILED, claim.refundStatus)
        val payment = paymentRow(seeded.orderGroupPublicId)
        assertEquals("APPROVED", payment.status, "선점이 풀려야 운영자 재집행이 막히지 않는다")
        assertEquals(16_000L, payment.cancellableAmount)
        assertEquals(OrderGroupStatus.PAID, groupStatusOf(seeded.orderGroupPublicId))
        assertEquals(OrderStatus.PAID, orderStatusOf(seeded.orderId))
        assertEquals(INITIAL_STOCK, stockOf(seeded.optionCombinationIds.single()))
        assertEquals(0L, pointBalanceOf(buyerId))
    }

    @Test
    fun `환불 실패 건은 집행 스캔에서 빠진다`() {
        val seeded = seedPaidOrder(listOf(ItemSpec(10_000L, 1)))
        val claimPublicId = approvedClaim(seeded)
        assertEquals(listOf(claimPublicId), claimRefundUseCase.findRefundPendingClaims(10).map { it.claimPublicId })
        paymentGateway.stubCancelStatus(seeded.orderGroupPublicId, PaymentCancelStatus.FAILED)
        assertThrows(BusinessException::class.java) {
            claimRefundUseCase.executeRefund(ExecuteClaimRefundCommand(claimPublicId))
        }

        assertEquals(emptyList<String>(), claimRefundUseCase.findRefundPendingClaims(10).map { it.claimPublicId })
    }

    @Test
    fun `PG가 비동기로 접수만 하면 원장과 그룹을 보류하고 요청 상태로 남긴다`() {
        val seeded = seedPaidOrder(listOf(ItemSpec(10_000L, 2)), usedPoint = 4_000L)
        val claimPublicId = approvedClaim(seeded)
        paymentGateway.stubCancelStatus(seeded.orderGroupPublicId, PaymentCancelStatus.REQUESTED)

        val result = claimRefundUseCase.executeRefund(ExecuteClaimRefundCommand(claimPublicId))

        assertEquals(ExecuteClaimRefundStatus.REFUND_REQUESTED, result.status)
        assertEquals(0L, result.releasedPoint)
        val payment = paymentRow(seeded.orderGroupPublicId)
        assertEquals("CANCEL_REQUESTED", payment.status)
        assertEquals(16_000L, payment.cancellableAmount, "PG도 아직 깎지 않았으므로 원장을 깎으면 대사가 금액 불일치 오보를 낸다")
        assertEquals(0L, payment.cancelledAmount)
        assertEquals(OrderGroupStatus.PAID, groupStatusOf(seeded.orderGroupPublicId))
        assertEquals(OrderStatus.PAID, orderStatusOf(seeded.orderId))
        assertEquals(INITIAL_STOCK, stockOf(seeded.optionCombinationIds.single()))
        assertEquals(0L, pointBalanceOf(buyerId))
        val claim = claimOf(claimPublicId)
        assertEquals(RefundStatus.REQUESTED, claim.refundStatus)
        assertNull(claim.resolvedAt)
    }

    @Test
    fun `전액 환불 뒤 도착한 결제 확정 요청은 취소된 주문으로 끊는다`() {
        val seeded = seedPaidOrder(listOf(ItemSpec(10_000L, 1)))
        claimRefundUseCase.executeRefund(ExecuteClaimRefundCommand(approvedClaim(seeded)))

        mockMvc
            .perform(post("/api/v1/order-groups/${seeded.orderGroupPublicId}/payment/complete").bearer(token))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.errorCode").value(PaymentErrorCode.PAYMENT_ORDER_GROUP_CANCELLED.code))
    }

    @Test
    fun `부분 환불 뒤 도착한 결제 확정 요청도 취소된 주문으로 끊는다`() {
        val seeded = seedPaidOrder(listOf(ItemSpec(10_000L, 1), ItemSpec(3_000L, 1)))
        claimRefundUseCase.executeRefund(
            ExecuteClaimRefundCommand(approvedClaim(seeded, targets = mapOf(seeded.orderItemIds[0] to 1))),
        )

        mockMvc
            .perform(post("/api/v1/order-groups/${seeded.orderGroupPublicId}/payment/complete").bearer(token))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.errorCode").value(PaymentErrorCode.PAYMENT_ORDER_GROUP_CANCELLED.code))
    }

    // ---------- 픽스처 ----------

    /** 주문그룹·셀러 주문·상품·옵션조합을 심고 결제 준비·확정을 태워 결제완료 상태로 만든다 */
    private fun seedPaidOrder(
        items: List<ItemSpec>,
        shippingFee: Long = 0L,
        usedPoint: Long = 0L,
    ): SeededOrder {
        val seeded =
            tx.execute {
                em.persist(Seller.open(sellerUserId, "멍냥상회", shippingFee))
                val productAmount = items.sumOf { it.unitPrice * it.quantity }
                val group =
                    OrderGroup.create(
                        buyerId = buyerId,
                        deliveryAddressId = 1L,
                        deliveryAddress =
                            DeliveryAddressSnapshot(
                                receiverNameEnc = "enc",
                                contactNumberEnc = "enc",
                                zipCode = "12345",
                                baseAddress = "서울시 애착구 멍냥로 1",
                                detailAddress = "101동",
                                deliveryMemo = null,
                            ),
                        usedPoint = usedPoint,
                        totalProductAmount = productAmount,
                        totalShippingFee = shippingFee,
                        idempotencyKey = "refund-$buyerId-${sig++}",
                        expiresAt = LocalDateTime.now().plusMinutes(10),
                    )
                em.persist(group)
                val orderItems = items.mapIndexed { index, spec -> orderItemOf(index, spec) }
                val order =
                    Order.create(
                        orderGroup = group,
                        sellerId = sellerUserId,
                        sellerNameSnapshot = "멍냥상회",
                        allocatedCouponDiscount = 0L,
                        sellerShippingFee = shippingFee,
                        baseShippingFeeSnapshot = shippingFee,
                        freeShippingThresholdSnapshot = null,
                        items = orderItems,
                    )
                em.persist(order)
                em.flush()
                SeededOrder(
                    orderId = order.id,
                    orderPublicId = order.publicId,
                    orderGroupPublicId = group.publicId,
                    orderItemIds = order.items.map { it.id },
                    optionCombinationIds = order.items.map { it.optionCombinationId },
                )
            }!!
        mockMvc
            .perform(
                post("/api/v1/order-groups/${seeded.orderGroupPublicId}/payment/prepare")
                    .bearer(token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"method":"KAKAO_PAY"}"""),
            ).andExpect(status().isCreated)
        mockMvc
            .perform(post("/api/v1/order-groups/${seeded.orderGroupPublicId}/payment/complete").bearer(token))
            .andExpect(status().isOk)
        em.clear()
        return seeded
    }

    /** 클레임을 요청 엔드포인트로 만들고 셀러 승인까지 태운다 — 승인은 상태만 바꾸고 환불은 집행하지 않는다 */
    private fun approvedClaim(
        seeded: SeededOrder,
        targets: Map<Long, Int>? = null,
    ): String {
        val itemsJson =
            targets?.entries?.joinToString(",", prefix = ""","items":[""", postfix = "]") {
                """{"orderItemId":${it.key},"quantity":${it.value}}"""
            } ?: ""
        val body = """{"claimType":"CANCEL","reasonCode":"CHANGE_OF_MIND"$itemsJson}"""
        val response =
            mockMvc
                .perform(
                    post("/api/v1/orders/${seeded.orderPublicId}/claims")
                        .bearer(token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body),
                ).andExpect(status().isCreated)
                .andReturn()
                .response
                .contentAsString
        val claimPublicId = JsonPath.read<String>(response, "$.data.claimPublicId")
        tx.execute {
            val claim =
                em
                    .createQuery("select c from Claim c where c.publicId = :pid", Claim::class.java)
                    .setParameter("pid", claimPublicId)
                    .singleResult
            claim.approveBySeller(null)
        }
        em.clear()
        return claimPublicId
    }

    private fun orderItemOf(
        index: Int,
        spec: ItemSpec,
    ): OrderItem {
        val productName = "멍냥 사료-$index"
        val category = Category.create(null, 1, "카테고리-${sig++}", null, 1)
        em.persist(category)
        val product = Product.register(category, sellerUserId, productName, null, null, spec.unitPrice, null, null, null)
        em.persist(product)
        em.persist(
            ProductVersion.snapshot(
                product = product,
                versionNo = 1,
                nameSnapshot = productName,
                priceSnapshot = spec.unitPrice,
                statusSnapshot = SaleStatus.ON_SALE,
                thumbnailKeySnapshot = "thumb-$index",
                changedBy = VersionChangedBy.SELLER,
            ),
        )
        val combo = OptionCombination.create(product, "기본 / 1개", 0L, INITIAL_STOCK, "sig-${sig++}")
        em.persist(combo)
        em.flush()
        return OrderItem.of(
            productId = product.id,
            optionCombinationId = combo.id,
            optionNameSnapshot = "기본 / 1개",
            quantity = spec.quantity,
            unitPriceSnapshot = spec.unitPrice,
            discountAllocatedAmount = 0L,
            productVersionId = 1L,
        )
    }

    private fun MockHttpServletRequestBuilder.bearer(token: String): MockHttpServletRequestBuilder =
        header(HttpHeaders.AUTHORIZATION, "Bearer $token")

    private fun claimOf(claimPublicId: String): Claim =
        tx.execute {
            em
                .createQuery("select c from Claim c where c.publicId = :pid", Claim::class.java)
                .setParameter("pid", claimPublicId)
                .singleResult
                .also { em.detach(it) }
        }!!

    private fun groupStatusOf(publicId: String): OrderGroupStatus =
        scalar("select g.status from OrderGroup g where g.publicId = :key", publicId, OrderGroupStatus::class.java)

    private fun orderStatusOf(orderId: Long): OrderStatus =
        scalar("select o.status from Order o where o.id = :key", orderId, OrderStatus::class.java)

    private fun itemStatusesOf(orderId: Long): List<OrderItemStatus> =
        tx.execute {
            em
                .createQuery(
                    "select distinct i.itemStatus from Order o join o._items i where o.id = :orderId",
                    OrderItemStatus::class.java,
                ).setParameter("orderId", orderId)
                .resultList
        }!!

    private fun stockOf(optionCombinationId: Long): Int =
        scalar("select c.stockQuantity from OptionCombination c where c.id = :key", optionCombinationId, Integer::class.java).toInt()

    private fun pointBalanceOf(userId: Long): Long =
        scalar("select u.pointBalance from User u where u.id = :key", userId, java.lang.Long::class.java).toLong()

    private fun pointLedgerCount(idempotencyKey: String): Int =
        scalar(
            "select count(t) from PointTransaction t where t.idempotencyKey = :key",
            idempotencyKey,
            java.lang.Long::class.java,
        ).toInt()

    private fun historyCount(
        orderId: Long,
        from: OrderStatus,
        to: OrderStatus,
    ): Int =
        tx.execute {
            em
                .createQuery(
                    "select count(h) from OrderStatusHistory h " +
                        "where h.order.id = :orderId and h.fromStatus = :from and h.toStatus = :to",
                    java.lang.Long::class.java,
                ).setParameter("orderId", orderId)
                .setParameter("from", from)
                .setParameter("to", to)
                .singleResult
                .toInt()
        }!!

    private fun <T> scalar(
        jpql: String,
        key: Any,
        type: Class<T>,
    ): T =
        tx.execute {
            em
                .createQuery(jpql, type)
                .setParameter("key", key)
                .singleResult
                .also { em.clear() }
        }!!

    private fun paymentRow(paymentId: String): PaymentRow =
        tx.execute {
            val row =
                em
                    .createQuery(
                        "select p.status, p.targetAmount, p.realPaidAmount, p.cancellableAmount, p.cancelledAmount " +
                            "from PaymentJpaEntity p where p.paymentId = :pid",
                        Array<Any>::class.java,
                    ).setParameter("pid", paymentId)
                    .singleResult
            em.clear()
            PaymentRow(
                status = row[0].toString(),
                targetAmount = (row[1] as Number).toLong(),
                realPaidAmount = (row[2] as Number).toLong(),
                cancellableAmount = (row[3] as Number).toLong(),
                cancelledAmount = (row[4] as Number).toLong(),
            )
        }!!

    private data class ItemSpec(
        val unitPrice: Long,
        val quantity: Int,
    )

    private data class SeededOrder(
        val orderId: Long,
        val orderPublicId: String,
        val orderGroupPublicId: String,
        val orderItemIds: List<Long>,
        val optionCombinationIds: List<Long>,
    )

    private data class PaymentRow(
        val status: String,
        val targetAmount: Long,
        val realPaidAmount: Long,
        val cancellableAmount: Long,
        val cancelledAmount: Long,
    )

    companion object {
        private const val INITIAL_STOCK = 100
    }
}
