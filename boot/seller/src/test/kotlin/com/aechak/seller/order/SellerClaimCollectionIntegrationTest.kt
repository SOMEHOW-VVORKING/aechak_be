package com.aechak.seller.order

import com.aechak.api.support.IntegrationTestBase
import com.aechak.domain.order.claim.Claim
import com.aechak.domain.order.claim.ClaimItem
import com.aechak.domain.order.claim.enums.ClaimStatus
import com.aechak.domain.order.claim.enums.ClaimType
import com.aechak.domain.order.group.DeliveryAddressSnapshot
import com.aechak.domain.order.group.OrderGroup
import com.aechak.domain.order.order.Order
import com.aechak.domain.order.order.OrderItem
import com.aechak.domain.order.order.enums.OrderItemStatus
import com.aechak.domain.order.order.enums.OrderStatus
import com.aechak.domain.order.shipment.CourierMaster
import com.aechak.domain.order.shipment.Shipment
import com.aechak.domain.order.shipment.enums.ShipmentType
import com.aechak.domain.seller.seller.Seller
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
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
 * 셀러 회수·재발송 엔드포인트 통합. 이 경로가 고정하는 것은 순서다 —
 * 승인 없이 회수가 시작되지 않고, 회수가 끝나기 전에 재발송이 열리지 않고,
 * 회수 송장과 재발송 송장이 배송 유형으로 갈려 한 클레임에 하나씩만 붙는다.
 */
class SellerClaimCollectionIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var context: WebApplicationContext

    @Autowired
    private lateinit var securityFilterChain: FilterChainProxy

    private lateinit var mockMvc: MockMvc
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
        sellerUserId = createActiveUser()
        token = mintAccessToken(sellerUserId)
        openSeller(sellerUserId)
        seedCourier()
    }

    @Test
    fun `승인된 반품에 회수 송장을 등록하면 204와 회수 시작 시각이 남는다`() {
        val seeded = seedClaim(ClaimType.RETURN, approved = true)

        mockMvc.perform(collectionRequest(token, seeded.claimPublicId)).andExpect(status().isNoContent)

        val claim = claimOf(seeded.claimPublicId)
        assertEquals(ClaimStatus.COLLECTING, claim.status, "회수 송장만으로 승인이 끝나면 안 된다")
        assertNotNull(claim.collectionStartedAt)
        val shipment = shipmentOf(claim.id, ShipmentType.RETURN_COLLECT)
        assertNotNull(shipment)
        assertEquals("987654321098", shipment!!.trackingNumber)
        assertEquals("CJ", shipment.courier.courierCode)
        assertNull(shipment.deliveredAt, "등록 시점에는 회수가 끝나지 않았다")
    }

    @Test
    fun `승인 전 반품에 회수 송장을 등록하면 409와 50409다`() {
        val seeded = seedClaim(ClaimType.RETURN, approved = false)

        mockMvc
            .perform(collectionRequest(token, seeded.claimPublicId))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.errorCode").value(50409))

        assertNull(claimOf(seeded.claimPublicId).collectionStartedAt)
        assertNull(shipmentOf(seeded.claimId, ShipmentType.RETURN_COLLECT), "거절된 요청이 송장을 남기면 안 된다")
    }

    @Test
    fun `취소 클레임에는 회수가 없다`() {
        val seeded = seedClaim(ClaimType.CANCEL, approved = true)

        mockMvc
            .perform(collectionRequest(token, seeded.claimPublicId))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.errorCode").value(50409))
    }

    @Test
    fun `회수 송장은 두 번 등록되지 않는다`() {
        val seeded = seedClaim(ClaimType.RETURN, approved = true)
        mockMvc.perform(collectionRequest(token, seeded.claimPublicId)).andExpect(status().isNoContent)

        mockMvc
            .perform(collectionRequest(token, seeded.claimPublicId))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.errorCode").value(50302))
    }

    @Test
    fun `회수를 시작하지 않고 완료하면 409와 50409다`() {
        val seeded = seedClaim(ClaimType.RETURN, approved = true)

        mockMvc
            .perform(post("/api/v1/sellers/me/claims/${seeded.claimPublicId}/collection/complete").bearer(token))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.errorCode").value(50409))

        assertEquals(ClaimStatus.COLLECTING, claimOf(seeded.claimPublicId).status)
    }

    @Test
    fun `반품 회수를 완료하면 품목이 회수 완료가 되고 클레임이 승인으로 끝난다`() {
        val seeded = seedClaim(ClaimType.RETURN, approved = true)
        mockMvc.perform(collectionRequest(token, seeded.claimPublicId)).andExpect(status().isNoContent)

        mockMvc.perform(completeRequest(token, seeded.claimPublicId)).andExpect(status().isNoContent)

        val claim = claimOf(seeded.claimPublicId)
        assertEquals(ClaimStatus.APPROVED, claim.status)
        assertEquals(listOf(OrderItemStatus.RETURNED), itemStatusesOf(seeded.orderId))
        assertNotNull(shipmentOf(claim.id, ShipmentType.RETURN_COLLECT)!!.deliveredAt)
    }

    @Test
    fun `교환 회수를 완료해도 품목은 교환 요청으로 남는다`() {
        val seeded = seedClaim(ClaimType.EXCHANGE, approved = true)
        mockMvc.perform(collectionRequest(token, seeded.claimPublicId)).andExpect(status().isNoContent)

        mockMvc.perform(completeRequest(token, seeded.claimPublicId)).andExpect(status().isNoContent)

        assertEquals(listOf(OrderItemStatus.EXCHANGE_REQUESTED), itemStatusesOf(seeded.orderId))
    }

    @Test
    fun `회수를 끝낸 교환에 재발송 송장을 등록하면 201과 교환 재발송 유형이 남는다`() {
        val seeded = seedClaim(ClaimType.EXCHANGE, approved = true)
        mockMvc.perform(collectionRequest(token, seeded.claimPublicId)).andExpect(status().isNoContent)
        mockMvc.perform(completeRequest(token, seeded.claimPublicId)).andExpect(status().isNoContent)

        mockMvc
            .perform(reshipRequest(token, seeded.claimPublicId))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.data.shipmentType").value("EXCHANGE_RESHIP"))
            .andExpect(jsonPath("$.data.trackingNumber").value("111222333444"))
            .andExpect(jsonPath("$.data.courierName").value("CJ대한통운"))

        val claim = claimOf(seeded.claimPublicId)
        assertNotNull(claim.resolvedAt, "재발송이 교환의 종결 사건이다")
        assertNotNull(shipmentOf(claim.id, ShipmentType.EXCHANGE_RESHIP))
    }

    @Test
    fun `회수가 끝나지 않은 교환의 재발송은 409와 50411이다`() {
        val seeded = seedClaim(ClaimType.EXCHANGE, approved = true)
        mockMvc.perform(collectionRequest(token, seeded.claimPublicId)).andExpect(status().isNoContent)

        mockMvc
            .perform(reshipRequest(token, seeded.claimPublicId))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.errorCode").value(50411))

        assertNull(shipmentOf(seeded.claimId, ShipmentType.EXCHANGE_RESHIP))
    }

    @Test
    fun `반품 클레임은 재발송 대상이 아니다`() {
        val seeded = seedClaim(ClaimType.RETURN, approved = true)
        mockMvc.perform(collectionRequest(token, seeded.claimPublicId)).andExpect(status().isNoContent)
        mockMvc.perform(completeRequest(token, seeded.claimPublicId)).andExpect(status().isNoContent)

        mockMvc
            .perform(reshipRequest(token, seeded.claimPublicId))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.errorCode").value(50411))
    }

    @Test
    fun `재발송은 두 번 되지 않는다`() {
        val seeded = seedClaim(ClaimType.EXCHANGE, approved = true)
        mockMvc.perform(collectionRequest(token, seeded.claimPublicId)).andExpect(status().isNoContent)
        mockMvc.perform(completeRequest(token, seeded.claimPublicId)).andExpect(status().isNoContent)
        mockMvc.perform(reshipRequest(token, seeded.claimPublicId)).andExpect(status().isCreated)

        mockMvc
            .perform(reshipRequest(token, seeded.claimPublicId))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.errorCode").value(50411))
    }

    @Test
    fun `타 셀러의 클레임 회수 등록은 404와 50400이다`() {
        val seeded = seedClaim(ClaimType.RETURN, approved = true)
        val otherSeller = createActiveUser()
        openSeller(otherSeller)

        mockMvc
            .perform(collectionRequest(mintAccessToken(otherSeller), seeded.claimPublicId))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.errorCode").value(50400))

        assertNull(claimOf(seeded.claimPublicId).collectionStartedAt)
    }

    @Test
    fun `타 셀러의 교환 재발송은 404와 50400이다`() {
        val seeded = seedClaim(ClaimType.EXCHANGE, approved = true)
        mockMvc.perform(collectionRequest(token, seeded.claimPublicId)).andExpect(status().isNoContent)
        mockMvc.perform(completeRequest(token, seeded.claimPublicId)).andExpect(status().isNoContent)
        val otherSeller = createActiveUser()
        openSeller(otherSeller)

        mockMvc
            .perform(reshipRequest(mintAccessToken(otherSeller), seeded.claimPublicId))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.errorCode").value(50400))

        assertNull(claimOf(seeded.claimPublicId).resolvedAt)
    }

    @Test
    fun `없는 택배사로는 회수 송장을 등록할 수 없다`() {
        val seeded = seedClaim(ClaimType.RETURN, approved = true)

        mockMvc
            .perform(
                post("/api/v1/sellers/me/claims/${seeded.claimPublicId}/collection")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"courierCode":"NOPE","trackingNumber":"987654321098"}""")
                    .bearer(token),
            ).andExpect(status().isNotFound)
            .andExpect(jsonPath("$.errorCode").value(50303))

        assertNull(claimOf(seeded.claimPublicId).collectionStartedAt)
    }

    @Test
    fun `송장번호가 비면 400이다`() {
        val seeded = seedClaim(ClaimType.RETURN, approved = true)

        mockMvc
            .perform(
                post("/api/v1/sellers/me/claims/${seeded.claimPublicId}/collection")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"courierCode":"CJ","trackingNumber":"  "}""")
                    .bearer(token),
            ).andExpect(status().isBadRequest)
    }

    @Test
    fun `미인증 회수 등록은 401과 20004다`() {
        val seeded = seedClaim(ClaimType.RETURN, approved = true)

        mockMvc
            .perform(
                post("/api/v1/sellers/me/claims/${seeded.claimPublicId}/collection")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"courierCode":"CJ","trackingNumber":"987654321098"}"""),
            ).andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.errorCode").value(20004))
    }

    // ---------- 헬퍼 ----------

    private fun collectionRequest(
        token: String,
        claimPublicId: String,
    ): MockHttpServletRequestBuilder =
        post("/api/v1/sellers/me/claims/$claimPublicId/collection")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"courierCode":"CJ","trackingNumber":"987654321098"}""")
            .bearer(token)

    private fun completeRequest(
        token: String,
        claimPublicId: String,
    ): MockHttpServletRequestBuilder = post("/api/v1/sellers/me/claims/$claimPublicId/collection/complete").bearer(token)

    private fun reshipRequest(
        token: String,
        claimPublicId: String,
    ): MockHttpServletRequestBuilder =
        post("/api/v1/sellers/me/claims/$claimPublicId/reship")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"courierCode":"CJ","trackingNumber":"111222333444"}""")
            .bearer(token)

    private fun MockHttpServletRequestBuilder.bearer(token: String): MockHttpServletRequestBuilder =
        this.header(HttpHeaders.AUTHORIZATION, "Bearer $token")

    private fun openSeller(userId: Long) {
        tx.execute { em.persist(Seller.open(userId, "애착상회$userId", 0L)) }
    }

    private fun seedCourier() {
        tx.execute { em.persist(CourierMaster.register("CJ", "CJ대한통운")) }
    }

    private fun claimOf(claimPublicId: String): Claim =
        tx.execute {
            em
                .createQuery("select c from Claim c where c.publicId = :publicId", Claim::class.java)
                .setParameter("publicId", claimPublicId)
                .singleResult
                .also { em.clear() }
        }!!

    private fun shipmentOf(
        claimId: Long,
        shipmentType: ShipmentType,
    ): Shipment? =
        tx.execute {
            em
                .createQuery(
                    "select s from Shipment s join fetch s.courier where s.claim.id = :claimId and s.shipmentType = :type",
                    Shipment::class.java,
                ).setParameter("claimId", claimId)
                .setParameter("type", shipmentType)
                .resultList
                .firstOrNull()
                .also { em.clear() }
        }

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

    private data class SeededClaim(
        val claimPublicId: String,
        val claimId: Long,
        val orderId: Long,
    )

    /** 배송완료 주문 1건과 그 주문 전체를 대상으로 하는 클레임 1건을 심는다. */
    private fun seedClaim(
        claimType: ClaimType,
        approved: Boolean,
    ): SeededClaim =
        tx.execute {
            val group =
                OrderGroup.create(
                    buyerId = 9_999L,
                    deliveryAddressId = 0L,
                    deliveryAddress =
                        DeliveryAddressSnapshot(
                            receiverNameEnc = "enc-receiver",
                            contactNumberEnc = "enc-contact",
                            zipCode = "12345",
                            baseAddress = "서울시 애착구 멍냥로 1",
                            detailAddress = null,
                            deliveryMemo = null,
                        ),
                    usedPoint = 0L,
                    totalProductAmount = 25_000L,
                    totalShippingFee = 0L,
                    idempotencyKey = "claim-collection-${sig++}-${System.nanoTime()}",
                    expiresAt = LocalDateTime.now().plusMinutes(10),
                )
            em.persist(group)
            val order =
                Order.create(
                    orderGroup = group,
                    sellerId = sellerUserId,
                    sellerNameSnapshot = "애착상회$sellerUserId",
                    allocatedCouponDiscount = 0L,
                    sellerShippingFee = 0L,
                    baseShippingFeeSnapshot = null,
                    freeShippingThresholdSnapshot = null,
                    items =
                        listOf(
                            OrderItem.of(
                                productId = 1L,
                                optionCombinationId = 2L,
                                optionNameSnapshot = "기본 / 1개",
                                quantity = 1,
                                unitPriceSnapshot = 25_000L,
                                discountAllocatedAmount = 0L,
                                productVersionId = 3L,
                            ),
                        ),
                )
            forceOrderStatus(order, if (claimType == ClaimType.CANCEL) OrderStatus.PAID else OrderStatus.DELIVERED)
            em.persist(order)
            em.flush()
            val claim =
                Claim.request(
                    order = order,
                    claimType = claimType,
                    reasonCode = "DEFECT",
                    reasonDetail = null,
                    items = listOf(ClaimItem.of(order.items.first(), quantity = 1, refundAmount = 25_000L)),
                    autoApproveDueAt = LocalDateTime.now().plusDays(2),
                )
            if (claimType != ClaimType.CANCEL) {
                order.items.first().markReturnOrExchangeRequested(claimType)
            }
            if (approved) {
                claim.approveBySeller(null)
            }
            em.persist(claim)
            em.flush()
            SeededClaim(claimPublicId = claim.publicId, claimId = claim.id, orderId = order.id)
        }!!

    private fun OrderItem.markReturnOrExchangeRequested(claimType: ClaimType) {
        if (claimType == ClaimType.RETURN) markReturnRequested() else markExchangeRequested()
    }

    /** 전이 API를 거치지 않고 상태를 심는다 — 주문 전이 규칙은 이 테스트의 대상이 아니다. */
    private fun forceOrderStatus(
        order: Order,
        status: OrderStatus,
    ) {
        Order::class.java
            .getDeclaredField("status")
            .apply { isAccessible = true }
            .set(order, status)
    }
}
