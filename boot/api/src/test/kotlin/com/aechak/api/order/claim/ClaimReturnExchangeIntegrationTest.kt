package com.aechak.api.order.claim

import com.aechak.api.support.FakePaymentGateway
import com.aechak.api.support.IntegrationTestBase
import com.aechak.application.order.claim.usecase.ClaimRefundUseCase
import com.aechak.application.order.claim.usecase.SellerClaimUseCase
import com.aechak.application.order.claim.usecase.command.ApproveClaimCommand
import com.aechak.application.order.claim.usecase.command.CompleteClaimCollectionCommand
import com.aechak.application.order.claim.usecase.command.ExecuteClaimRefundCommand
import com.aechak.application.order.claim.usecase.command.RejectClaimCommand
import com.aechak.application.order.claim.usecase.command.ReshipClaimCommand
import com.aechak.application.order.claim.usecase.command.StartClaimCollectionCommand
import com.aechak.application.order.claim.usecase.result.ExecuteClaimRefundStatus
import com.aechak.common.error.BusinessException
import com.aechak.domain.order.claim.Claim
import com.aechak.domain.order.claim.enums.ClaimStatus
import com.aechak.domain.order.claim.enums.ClaimType
import com.aechak.domain.order.claim.enums.RefundStatus
import com.aechak.domain.order.claim.enums.ReturnCostBearer
import com.aechak.domain.order.error.OrderErrorCode
import com.aechak.domain.order.group.DeliveryAddressSnapshot
import com.aechak.domain.order.group.OrderGroup
import com.aechak.domain.order.group.enums.OrderGroupStatus
import com.aechak.domain.order.order.Order
import com.aechak.domain.order.order.OrderItem
import com.aechak.domain.order.order.OrderStatusHistory
import com.aechak.domain.order.order.enums.OrderItemStatus
import com.aechak.domain.order.order.enums.OrderStatus
import com.aechak.domain.order.order.enums.TriggeredBy
import com.aechak.domain.order.shipment.CourierMaster
import com.aechak.domain.order.shipment.Shipment
import com.aechak.domain.order.shipment.enums.ShipmentType
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
 * 반품·교환 전체 경로 통합. 이 경로가 고정하는 것은 셋이다 —
 * 반품 환불은 회수가 끝난 뒤에만 집행된다는 것(물건을 받기 전에 돈이 나가면 안 된다),
 * 교환은 환불도 재고·적립금 복원도 없이 재발송으로 끝난다는 것,
 * 반품 기한은 배송완료 시각이 기산점이고 기한이 지나면 요청 자체를 받지 않는다는 것.
 * 집행 트리거는 엔드포인트가 아니라 배치라서 환불은 내부 포트를 직접 부른다.
 */
class ClaimReturnExchangeIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var context: WebApplicationContext

    @Autowired
    private lateinit var securityFilterChain: FilterChainProxy

    @Autowired
    private lateinit var paymentGateway: FakePaymentGateway

    @Autowired
    private lateinit var claimRefundUseCase: ClaimRefundUseCase

    @Autowired
    private lateinit var sellerClaimUseCase: SellerClaimUseCase

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
    fun `반품은 회수가 끝난 뒤에야 환불된다`() {
        val seeded = seedDeliveredOrder(listOf(ItemSpec(10_000L, 2)))
        val claimPublicId = requestClaim(seeded, ClaimType.RETURN)

        assertEquals(listOf(OrderItemStatus.RETURN_REQUESTED), itemStatusesOf(seeded.orderId))
        assertEquals(ClaimStatus.CLAIM_REQUESTED, claimOf(claimPublicId).status)

        sellerClaimUseCase.approveClaim(ApproveClaimCommand(sellerUserId, claimPublicId, ReturnCostBearer.SELLER))
        assertEquals(ClaimStatus.COLLECTING, claimOf(claimPublicId).status)

        // 회수 전 집행은 선점에 걸리지 않는다 — 걸리면 물건 없이 돈이 나간다
        assertEquals(
            ExecuteClaimRefundStatus.SKIPPED,
            claimRefundUseCase.executeRefund(ExecuteClaimRefundCommand(claimPublicId)).status,
        )
        assertEquals(0, paymentGateway.cancelCommands.size)

        startCollection(claimPublicId)
        assertEquals(
            ExecuteClaimRefundStatus.SKIPPED,
            claimRefundUseCase.executeRefund(ExecuteClaimRefundCommand(claimPublicId)).status,
            "회수 송장만 등록된 건도 집행 대상이 아니다",
        )
        assertEquals(0, paymentGateway.cancelCommands.size)

        sellerClaimUseCase.completeCollection(CompleteClaimCollectionCommand(sellerUserId, claimPublicId))
        assertEquals(ClaimStatus.APPROVED, claimOf(claimPublicId).status)
        assertEquals(listOf(OrderItemStatus.RETURNED), itemStatusesOf(seeded.orderId))

        val result = claimRefundUseCase.executeRefund(ExecuteClaimRefundCommand(claimPublicId))

        assertEquals(ExecuteClaimRefundStatus.REFUNDED, result.status)
        assertEquals(20_000L, result.refundAmount)
        assertEquals(1, paymentGateway.cancelCommands.size)
        assertEquals(RefundStatus.DONE, claimOf(claimPublicId).refundStatus)
        assertEquals(OrderGroupStatus.CANCELLED, groupStatusOf(seeded.orderGroupPublicId))
        assertEquals(OrderStatus.CANCELLED, orderStatusOf(seeded.orderId), "이행 대상이 없는 주문을 열어 두면 자동 구매확정이 집는다")
        assertEquals(INITIAL_STOCK + 2, stockOf(seeded.optionCombinationIds.single()), "회수한 물건은 재고로 돌아간다")
    }

    @Test
    fun `구매자가 부담하는 반품 발송비는 환불액에서 빠진다`() {
        val seeded = seedDeliveredOrder(listOf(ItemSpec(10_000L, 2)))
        val claimPublicId = collectedClaim(seeded, ClaimType.RETURN, ReturnCostBearer.BUYER)

        val result = claimRefundUseCase.executeRefund(ExecuteClaimRefundCommand(claimPublicId))

        assertEquals(15_000L, result.refundAmount, "반품비 5,000원이 빠진 금액만 돌려줘야 한다")
        assertEquals(15_000L, paymentGateway.cancelCommands.single().amount)
        assertEquals(OrderGroupStatus.PARTIALLY_CANCELLED, groupStatusOf(seeded.orderGroupPublicId), "취소가능액이 남으면 부분취소다")
    }

    @Test
    fun `교환은 회수 뒤 재발송으로 끝나고 환불도 복원도 없다`() {
        val seeded = seedDeliveredOrder(listOf(ItemSpec(10_000L, 2)))
        val claimPublicId = requestClaim(seeded, ClaimType.EXCHANGE)

        assertEquals(listOf(OrderItemStatus.EXCHANGE_REQUESTED), itemStatusesOf(seeded.orderId))
        sellerClaimUseCase.approveClaim(ApproveClaimCommand(sellerUserId, claimPublicId, ReturnCostBearer.SELLER))
        startCollection(claimPublicId)
        sellerClaimUseCase.completeCollection(CompleteClaimCollectionCommand(sellerUserId, claimPublicId))

        val reshipped =
            sellerClaimUseCase.reship(
                ReshipClaimCommand(sellerUserId, claimPublicId, courierCode = "CJ", trackingNumber = "111222333444"),
            )

        assertEquals(ShipmentType.EXCHANGE_RESHIP, reshipped.shipmentType)
        assertNotNull(reshipped.dispatchedAt)
        assertNotNull(shipmentOf(claimPublicId, ShipmentType.EXCHANGE_RESHIP))
        assertNotNull(shipmentOf(claimPublicId, ShipmentType.RETURN_COLLECT), "회수 송장과 재발송 송장은 유형으로 갈려 함께 남는다")
        val claim = claimOf(claimPublicId)
        assertEquals(RefundStatus.NONE, claim.refundStatus, "교환에는 환불이 없다")
        assertNotNull(claim.resolvedAt)
        assertEquals(0, paymentGateway.cancelCommands.size)
        assertEquals(INITIAL_STOCK, stockOf(seeded.optionCombinationIds.single()), "교환은 재고를 되돌리지 않는다")
        assertEquals(OrderStatus.DELIVERED, orderStatusOf(seeded.orderId))
    }

    @Test
    fun `교환 클레임은 환불 집행 경로로 들어오지 않는다`() {
        val seeded = seedDeliveredOrder(listOf(ItemSpec(10_000L, 1)))
        val claimPublicId = collectedClaim(seeded, ClaimType.EXCHANGE, ReturnCostBearer.SELLER)

        val exception =
            assertThrows(BusinessException::class.java) {
                claimRefundUseCase.executeRefund(ExecuteClaimRefundCommand(claimPublicId))
            }

        assertEquals(OrderErrorCode.INVALID_CLAIM_STATUS_TRANSITION, exception.errorCode)
        assertEquals(0, paymentGateway.cancelCommands.size)
    }

    @Test
    fun `반품 기한이 지난 주문은 409와 50401이다`() {
        val seeded = seedDeliveredOrder(listOf(ItemSpec(10_000L, 1)), deliveredDaysAgo = 8)

        mockMvc
            .perform(claimRequest(seeded, ClaimType.RETURN))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.errorCode").value(50401))

        assertEquals(listOf(OrderItemStatus.ORDERED), itemStatusesOf(seeded.orderId))
        assertEquals(0, claimCountOf(seeded.orderId))
    }

    @Test
    fun `기한 마지막 날의 반품은 받는다`() {
        val seeded = seedDeliveredOrder(listOf(ItemSpec(10_000L, 1)), deliveredDaysAgo = 6)

        mockMvc.perform(claimRequest(seeded, ClaimType.RETURN)).andExpect(status().isCreated)

        assertEquals(1, claimCountOf(seeded.orderId))
    }

    @Test
    fun `배송완료 이력이 없으면 기산점을 몰라 반품을 받지 않는다`() {
        val seeded = seedDeliveredOrder(listOf(ItemSpec(10_000L, 1)), recordHistory = false)

        mockMvc
            .perform(claimRequest(seeded, ClaimType.RETURN))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.errorCode").value(50401))
    }

    @Test
    fun `배송완료 주문의 취소 요청은 409와 50401이다`() {
        val seeded = seedDeliveredOrder(listOf(ItemSpec(10_000L, 1)))

        mockMvc
            .perform(claimRequest(seeded, ClaimType.CANCEL))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.errorCode").value(50401))
    }

    @Test
    fun `거부된 반품은 품목을 주문 상태로 되돌려 다시 요청할 수 있게 한다`() {
        val seeded = seedDeliveredOrder(listOf(ItemSpec(10_000L, 1)))
        val claimPublicId = requestClaim(seeded, ClaimType.RETURN)

        sellerClaimUseCase.rejectClaim(RejectClaimCommand(sellerUserId, claimPublicId, "하자가 확인되지 않습니다"))

        assertEquals(ClaimStatus.REJECTED, claimOf(claimPublicId).status)
        assertEquals(listOf(OrderItemStatus.ORDERED), itemStatusesOf(seeded.orderId), "되돌리지 않으면 다시 반품할 수 없다")
        mockMvc.perform(claimRequest(seeded, ClaimType.RETURN)).andExpect(status().isCreated)
    }

    @Test
    fun `진행 중인 반품이 있으면 같은 품목에 교환을 걸 수 없다`() {
        val seeded = seedDeliveredOrder(listOf(ItemSpec(10_000L, 1)))
        requestClaim(seeded, ClaimType.RETURN)

        mockMvc
            .perform(claimRequest(seeded, ClaimType.EXCHANGE))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.errorCode").value(50403))
    }

    // ---------- 픽스처 ----------

    private fun requestClaim(
        seeded: SeededOrder,
        claimType: ClaimType,
    ): String {
        val response =
            mockMvc
                .perform(claimRequest(seeded, claimType))
                .andExpect(status().isCreated)
                .andReturn()
                .response
                .contentAsString
        em.clear()
        return JsonPath.read<String>(response, "$.data.claimPublicId")
    }

    private fun claimRequest(
        seeded: SeededOrder,
        claimType: ClaimType,
    ): MockHttpServletRequestBuilder =
        post("/api/v1/orders/${seeded.orderPublicId}/claims")
            .bearer(token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"claimType":"$claimType","reasonCode":"DEFECT"}""")

    private fun startCollection(claimPublicId: String) {
        sellerClaimUseCase.startCollection(
            StartClaimCollectionCommand(sellerUserId, claimPublicId, courierCode = "CJ", trackingNumber = "987654321098"),
        )
        em.clear()
    }

    /** 요청부터 회수 완료까지 태운 클레임을 돌려준다 — 환불 집행만 남은 상태. */
    private fun collectedClaim(
        seeded: SeededOrder,
        claimType: ClaimType,
        returnCostBearer: ReturnCostBearer,
    ): String {
        val claimPublicId = requestClaim(seeded, claimType)
        sellerClaimUseCase.approveClaim(ApproveClaimCommand(sellerUserId, claimPublicId, returnCostBearer))
        startCollection(claimPublicId)
        sellerClaimUseCase.completeCollection(CompleteClaimCollectionCommand(sellerUserId, claimPublicId))
        em.clear()
        return claimPublicId
    }

    /**
     * 결제완료까지 태운 주문을 배송완료로 옮기고 전이 이력을 남긴다 —
     * 배송 전이 경로는 이 테스트의 대상이 아니라 상태와 이력만 심는다.
     */
    private fun seedDeliveredOrder(
        items: List<ItemSpec>,
        deliveredDaysAgo: Long = 0,
        recordHistory: Boolean = true,
    ): SeededOrder {
        val seeded =
            tx.execute {
                em.persist(Seller.open(sellerUserId, "멍냥상회", 0L))
                em.persist(CourierMaster.register("CJ", "CJ대한통운"))
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
                        usedPoint = 0L,
                        totalProductAmount = productAmount,
                        totalShippingFee = 0L,
                        idempotencyKey = "return-$buyerId-${sig++}",
                        expiresAt = LocalDateTime.now().plusMinutes(10),
                    )
                em.persist(group)
                val order =
                    Order.create(
                        orderGroup = group,
                        sellerId = sellerUserId,
                        sellerNameSnapshot = "멍냥상회",
                        allocatedCouponDiscount = 0L,
                        sellerShippingFee = 0L,
                        baseShippingFeeSnapshot = 0L,
                        freeShippingThresholdSnapshot = null,
                        items = items.mapIndexed { index, spec -> orderItemOf(index, spec) },
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
        payFor(seeded)
        tx.execute {
            val order =
                em
                    .createQuery("select o from Order o where o.id = :id", Order::class.java)
                    .setParameter("id", seeded.orderId)
                    .singleResult
            order.accept()
            order.markDispatchPending()
            order.markShipping()
            order.markDelivered(LocalDateTime.now().plusDays(7))
            if (recordHistory) {
                val history =
                    OrderStatusHistory.record(
                        order = order,
                        fromStatus = OrderStatus.SHIPPING,
                        toStatus = OrderStatus.DELIVERED,
                        triggeredBy = TriggeredBy.SELLER,
                    )
                em.persist(history)
                em.flush()
                backdate(history.id, deliveredDaysAgo)
            }
        }
        em.clear()
        return seeded
    }

    /** 전이 이력의 created_at이 배송완료 시각이다 — 기한 경과를 만들려면 이 값을 과거로 옮긴다. */
    private fun backdate(
        historyId: Long,
        daysAgo: Long,
    ) {
        if (daysAgo <= 0) return
        em
            .createNativeQuery("update order_status_histories set created_at = ? where id = ?")
            .setParameter(1, LocalDateTime.now().minusDays(daysAgo))
            .setParameter(2, historyId)
            .executeUpdate()
    }

    private fun payFor(seeded: SeededOrder) {
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

    private fun shipmentOf(
        claimPublicId: String,
        shipmentType: ShipmentType,
    ): Shipment? =
        tx.execute {
            em
                .createQuery(
                    "select s from Shipment s where s.claim.publicId = :pid and s.shipmentType = :type",
                    Shipment::class.java,
                ).setParameter("pid", claimPublicId)
                .setParameter("type", shipmentType)
                .resultList
                .firstOrNull()
                .also { em.clear() }
        }

    private fun claimCountOf(orderId: Long): Int =
        scalar("select count(c) from Claim c where c.orderId = :key", orderId, java.lang.Long::class.java).toInt()

    private fun groupStatusOf(publicId: String): OrderGroupStatus =
        scalar("select g.status from OrderGroup g where g.publicId = :key", publicId, OrderGroupStatus::class.java)

    private fun orderStatusOf(orderId: Long): OrderStatus =
        scalar("select o.status from Order o where o.id = :key", orderId, OrderStatus::class.java)

    private fun stockOf(optionCombinationId: Long): Int =
        scalar("select c.stockQuantity from OptionCombination c where c.id = :key", optionCombinationId, Integer::class.java).toInt()

    private fun itemStatusesOf(orderId: Long): List<OrderItemStatus> =
        tx.execute {
            em
                .createQuery(
                    "select distinct i.itemStatus from Order o join o._items i where o.id = :orderId",
                    OrderItemStatus::class.java,
                ).setParameter("orderId", orderId)
                .resultList
                .also { em.clear() }
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

    companion object {
        private const val INITIAL_STOCK = 100
    }
}
