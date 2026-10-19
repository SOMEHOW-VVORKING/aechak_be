package com.aechak.api.order.claim

import com.aechak.api.support.IntegrationTestBase
import com.aechak.domain.order.claim.enums.ClaimStatus
import com.aechak.domain.order.group.DeliveryAddressSnapshot
import com.aechak.domain.order.group.OrderGroup
import com.aechak.domain.order.order.Order
import com.aechak.domain.order.order.OrderItem
import com.aechak.domain.order.order.enums.OrderStatus
import com.aechak.domain.product.category.Category
import com.aechak.domain.product.option.OptionCombination
import com.aechak.domain.product.product.Product
import com.aechak.domain.product.product.enums.SaleStatus
import com.aechak.domain.product.version.ProductVersion
import com.aechak.domain.product.version.enums.VersionChangedBy
import com.aechak.domain.seller.seller.Seller
import com.jayway.jsonpath.JsonPath
import org.hamcrest.Matchers.containsInAnyOrder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.web.FilterChainProxy
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.time.LocalDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 구매자 클레임 요청·조회·철회 통합. 환불액은 요청 시점에 claim_items로 확정 저장되므로
 * 그 합이 결제액과 어긋나면 이후 집행이 결제액과 갈린다 — 그 계약을 여기서 고정한다.
 */
class ClaimRequestIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var context: WebApplicationContext

    @Autowired
    private lateinit var securityFilterChain: FilterChainProxy

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
        buyerId = createActiveUser()
        token = mintAccessToken(buyerId)
        sellerUserId = createActiveUser()
    }

    @Test
    fun `결제완료 주문을 전체 취소 요청하면 201과 결제액 전액이 확정된다`() {
        val seeded = seedOrder(items = listOf(ItemSpec(20_000L, 1), ItemSpec(15_500L, 2)), shippingFee = 3_000L)

        val body =
            mockMvc
                .perform(requestClaim(token, seeded.orderPublicId, """{"claimType":"CANCEL","reasonCode":"CHANGE_OF_MIND"}"""))
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.data.status").value(ClaimStatus.CLAIM_REQUESTED.name))
                .andExpect(jsonPath("$.data.sellerApprovalStatus").value("REQUESTED"))
                .andExpect(jsonPath("$.data.refundStatus").doesNotExist())
                .andReturn()
                .response
                .getContentAsString(Charsets.UTF_8)

        assertEquals(seeded.finalPaymentAmount, JsonPath.read<Int>(body, "$.data.refundAmount").toLong())
        assertEquals(1, claimCount(seeded.orderId))
        assertEquals(2, claimItemCount(seeded.orderId), "대상 품목마다 claim_items 1행이어야 한다")
        assertEquals(seeded.productAmount, claimItemRefundTotal(seeded.orderId), "품목 환불액 합은 상품금액이어야 한다")
        assertTrue(JsonPath.read<String>(body, "$.data.autoApproveDueAt").isNotBlank(), "자동승인 기한이 비면 배치가 집어갈 수 없다")
    }

    @Test
    fun `적립금을 쓴 주문의 전체 취소 환불액은 결제액과 같다`() {
        val seeded =
            seedOrder(
                items = listOf(ItemSpec(20_000L, 1), ItemSpec(15_500L, 2)),
                shippingFee = 3_000L,
                usedPoint = 10_000L,
            )

        val body =
            mockMvc
                .perform(requestClaim(token, seeded.orderPublicId, """{"claimType":"CANCEL","reasonCode":"CHANGE_OF_MIND"}"""))
                .andExpect(status().isCreated)
                .andReturn()
                .response
                .getContentAsString(Charsets.UTF_8)

        assertEquals(seeded.finalPaymentAmount, JsonPath.read<Int>(body, "$.data.refundAmount").toLong())
        assertTrue(
            claimItemRefundTotal(seeded.orderId) < seeded.productAmount,
            "적립금으로 낸 몫은 현금 환불액에서 빠져야 한다",
        )
    }

    /** 적립금 사용 상한은 상품금액이 아니라 배송비까지 더한 결제 대상 금액이다. */
    @Test
    fun `적립금이 상품금액을 넘어도 환불액은 결제액을 넘지 않는다`() {
        val seeded = seedOrder(items = listOf(ItemSpec(1_000L, 1)), shippingFee = 3_000L, usedPoint = 2_000L)

        val body =
            mockMvc
                .perform(requestClaim(token, seeded.orderPublicId, """{"claimType":"CANCEL","reasonCode":"CHANGE_OF_MIND"}"""))
                .andExpect(status().isCreated)
                .andReturn()
                .response
                .getContentAsString(Charsets.UTF_8)

        assertEquals(2_000L, seeded.finalPaymentAmount, "전제: 결제액은 2,000원이다")
        assertEquals(seeded.finalPaymentAmount, JsonPath.read<Int>(body, "$.data.refundAmount").toLong())
    }

    /** 중복 검사는 조회라 선점 없이는 겹친 요청이 둘 다 통과한다. */
    @Test
    fun `동시 취소 요청은 하나만 성공한다`() {
        val seeded = seedOrder(items = listOf(ItemSpec(20_000L, 1)))

        val results =
            raceTwo { requestClaim(token, seeded.orderPublicId, """{"claimType":"CANCEL","reasonCode":"CHANGE_OF_MIND"}""") }

        assertEquals(listOf(201, 409), results.map { it.first }.sorted(), "결과: $results")
        assertEquals("50403", errorCodeOf(results.first { it.first == 409 }.second))
        assertEquals(1, claimCount(seeded.orderId), "같은 품목에 클레임이 두 건 걸리면 환불이 두 번 나간다")
    }

    @Test
    fun `일부 품목만 취소 요청하면 그 품목의 금액만 환불액이 된다`() {
        val seeded = seedOrder(items = listOf(ItemSpec(20_000L, 1), ItemSpec(15_500L, 2)), shippingFee = 3_000L)
        val target = seeded.orderItemIds[1]

        val body =
            mockMvc
                .perform(
                    requestClaim(
                        token,
                        seeded.orderPublicId,
                        """{"claimType":"CANCEL","reasonCode":"CHANGE_OF_MIND","items":[{"orderItemId":$target,"quantity":2}]}""",
                    ),
                ).andExpect(status().isCreated)
                .andReturn()
                .response
                .getContentAsString(Charsets.UTF_8)

        assertEquals(31_000L, JsonPath.read<Int>(body, "$.data.refundAmount").toLong(), "부분 취소는 배송비를 돌려주지 않는다")
        assertEquals(1, claimItemCount(seeded.orderId))
        assertEquals(31_000L, claimItemRefundTotal(seeded.orderId))
    }

    @Test
    fun `발송된 주문의 취소 요청은 409와 50401이다`() {
        val seeded = seedOrder(items = listOf(ItemSpec(20_000L, 1)), status = OrderStatus.SHIPPING)

        mockMvc
            .perform(requestClaim(token, seeded.orderPublicId, """{"claimType":"CANCEL","reasonCode":"CHANGE_OF_MIND"}"""))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.errorCode").value(50401))

        assertEquals(0, claimCount(seeded.orderId))
    }

    @Test
    fun `같은 주문품목에 진행 중 클레임이 있으면 409와 50403이다`() {
        val seeded = seedOrder(items = listOf(ItemSpec(20_000L, 1)))
        mockMvc
            .perform(requestClaim(token, seeded.orderPublicId, """{"claimType":"CANCEL","reasonCode":"CHANGE_OF_MIND"}"""))
            .andExpect(status().isCreated)

        mockMvc
            .perform(requestClaim(token, seeded.orderPublicId, """{"claimType":"CANCEL","reasonCode":"BROKEN"}"""))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.errorCode").value(50403))

        assertEquals(1, claimCount(seeded.orderId))
    }

    @Test
    fun `철회된 클레임이 있으면 같은 품목에 다시 요청할 수 있다`() {
        val seeded = seedOrder(items = listOf(ItemSpec(20_000L, 1)))
        val claimPublicId = requestedClaimPublicId(seeded.orderPublicId)
        mockMvc
            .perform(withdrawClaim(token, seeded.orderPublicId, claimPublicId))
            .andExpect(status().isNoContent)

        mockMvc
            .perform(requestClaim(token, seeded.orderPublicId, """{"claimType":"CANCEL","reasonCode":"BROKEN"}"""))
            .andExpect(status().isCreated)

        assertEquals(2, claimCount(seeded.orderId))
    }

    /**
     * 수량을 쪼갠 요청은 받지 않는다 — 중복 차단이 품목 단위라 남은 수량에 후속 클레임을 걸 수 없고,
     * order_items에 취소 수량을 담을 자리도 없어 남은 수량이 환불도 이행도 받지 못한 채 묶인다.
     */
    @Test
    fun `주문 수량보다 적게 요청하면 422와 50410이다`() {
        val seeded = seedOrder(items = listOf(ItemSpec(20_000L, 3)))
        val target = seeded.orderItemIds.first()

        mockMvc
            .perform(
                requestClaim(
                    token,
                    seeded.orderPublicId,
                    """{"claimType":"CANCEL","reasonCode":"CHANGE_OF_MIND","items":[{"orderItemId":$target,"quantity":1}]}""",
                ),
            ).andExpect(status().isUnprocessableEntity)
            .andExpect(jsonPath("$.errorCode").value(50410))

        assertEquals(0, claimCount(seeded.orderId))
    }

    @Test
    fun `주문 수량보다 많이 요청하면 422와 50406이다`() {
        val seeded = seedOrder(items = listOf(ItemSpec(20_000L, 2)))
        val target = seeded.orderItemIds.first()

        mockMvc
            .perform(
                requestClaim(
                    token,
                    seeded.orderPublicId,
                    """{"claimType":"CANCEL","reasonCode":"CHANGE_OF_MIND","items":[{"orderItemId":$target,"quantity":3}]}""",
                ),
            ).andExpect(status().isUnprocessableEntity)
            .andExpect(jsonPath("$.errorCode").value(50406))

        assertEquals(0, claimCount(seeded.orderId))
    }

    @Test
    fun `타인 주문의 취소 요청은 404와 50000이다`() {
        val seeded = seedOrder(items = listOf(ItemSpec(20_000L, 1)))
        val other = createActiveUser()

        mockMvc
            .perform(
                requestClaim(mintAccessToken(other), seeded.orderPublicId, """{"claimType":"CANCEL","reasonCode":"CHANGE_OF_MIND"}"""),
            ).andExpect(status().isNotFound)
            .andExpect(jsonPath("$.errorCode").value(50000))

        assertEquals(0, claimCount(seeded.orderId))
    }

    @Test
    fun `사유 코드가 비면 400과 90001이다`() {
        val seeded = seedOrder(items = listOf(ItemSpec(20_000L, 1)))

        mockMvc
            .perform(requestClaim(token, seeded.orderPublicId, """{"claimType":"CANCEL","reasonCode":"  "}"""))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(90001))
    }

    @Test
    fun `클레임을 철회하면 204와 철회 상태가 된다`() {
        val seeded = seedOrder(items = listOf(ItemSpec(20_000L, 1)))
        val claimPublicId = requestedClaimPublicId(seeded.orderPublicId)

        mockMvc
            .perform(withdrawClaim(token, seeded.orderPublicId, claimPublicId))
            .andExpect(status().isNoContent)

        assertEquals(ClaimStatus.WITHDRAWN, statusOf(claimPublicId))
    }

    @Test
    fun `타인 주문 경로로는 철회할 수 없다`() {
        val seeded = seedOrder(items = listOf(ItemSpec(20_000L, 1)))
        val claimPublicId = requestedClaimPublicId(seeded.orderPublicId)
        val other = createActiveUser()

        mockMvc
            .perform(withdrawClaim(mintAccessToken(other), seeded.orderPublicId, claimPublicId))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.errorCode").value(50000))

        assertEquals(ClaimStatus.CLAIM_REQUESTED, statusOf(claimPublicId))
    }

    @Test
    fun `클레임은 셀러와 주문 식별자를 스냅샷으로 저장한다`() {
        val seeded = seedOrder(items = listOf(ItemSpec(20_000L, 1)))

        mockMvc
            .perform(requestClaim(token, seeded.orderPublicId, """{"claimType":"CANCEL","reasonCode":"CHANGE_OF_MIND"}"""))
            .andExpect(status().isCreated)

        assertEquals(sellerUserId, claimScalar(seeded.orderId, "sellerId"))
        assertEquals(seeded.orderId, claimScalar(seeded.orderId, "orderId"))
    }

    @Test
    fun `클레임 목록은 품목 상품명과 확정 환불액을 함께 내린다`() {
        val seeded = seedOrder(items = listOf(ItemSpec(20_000L, 1), ItemSpec(15_500L, 2)), shippingFee = 3_000L)
        mockMvc
            .perform(requestClaim(token, seeded.orderPublicId, """{"claimType":"CANCEL","reasonCode":"CHANGE_OF_MIND"}"""))
            .andExpect(status().isCreated)

        mockMvc
            .perform(get("/api/v1/orders/${seeded.orderPublicId}/claims").bearer(token))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.items.length()").value(1))
            .andExpect(jsonPath("$.data.items[0].status").value(ClaimStatus.CLAIM_REQUESTED.name))
            .andExpect(jsonPath("$.data.items[0].refundStatus").value("NONE"))
            .andExpect(jsonPath("$.data.items[0].returnCostBearer").doesNotExist())
            .andExpect(jsonPath("$.data.items[0].refundAmount").value(seeded.finalPaymentAmount))
            .andExpect(jsonPath("$.data.items[0].items.length()").value(2))
            .andExpect(
                jsonPath("$.data.items[0].items[*].productName")
                    .value(containsInAnyOrder("멍냥 사료-0", "멍냥 사료-1")),
            ).andExpect(
                jsonPath("$.data.items[0].items[*].refundAmount")
                    .value(containsInAnyOrder(20_000, 31_000)),
            )
    }

    @Test
    fun `타인 주문의 클레임 목록은 404와 50000이다`() {
        val seeded = seedOrder(items = listOf(ItemSpec(20_000L, 1)))
        val other = createActiveUser()

        mockMvc
            .perform(get("/api/v1/orders/${seeded.orderPublicId}/claims").bearer(mintAccessToken(other)))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.errorCode").value(50000))
    }

    // ---------- 헬퍼 ----------

    private fun requestClaim(
        token: String,
        orderPublicId: String,
        body: String,
    ): MockHttpServletRequestBuilder =
        post("/api/v1/orders/$orderPublicId/claims")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body)
            .bearer(token)

    private fun withdrawClaim(
        token: String,
        orderPublicId: String,
        claimPublicId: String,
    ): MockHttpServletRequestBuilder = delete("/api/v1/orders/$orderPublicId/claims/$claimPublicId").bearer(token)

    private fun MockHttpServletRequestBuilder.bearer(token: String): MockHttpServletRequestBuilder =
        this.header(HttpHeaders.AUTHORIZATION, "Bearer $token")

    private fun errorCodeOf(body: String): String? = Regex("\"errorCode\"\\s*:\\s*(\\d+)").find(body)?.groupValues?.get(1)

    private fun raceTwo(request: () -> MockHttpServletRequestBuilder): List<Pair<Int, String>> {
        val pool = Executors.newFixedThreadPool(2)
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        try {
            val futures =
                listOf(request(), request()).map { builder ->
                    pool.submit<Pair<Int, String>> {
                        ready.countDown()
                        start.await()
                        val response = mockMvc.perform(builder).andReturn().response
                        response.status to response.getContentAsString(Charsets.UTF_8)
                    }
                }
            ready.await()
            start.countDown()
            return futures.map { it.get(60, TimeUnit.SECONDS) }
        } finally {
            pool.shutdown()
        }
    }

    private fun requestedClaimPublicId(orderPublicId: String): String {
        val body =
            mockMvc
                .perform(requestClaim(token, orderPublicId, """{"claimType":"CANCEL","reasonCode":"CHANGE_OF_MIND"}"""))
                .andExpect(status().isCreated)
                .andReturn()
                .response
                .getContentAsString(Charsets.UTF_8)
        return JsonPath.read(body, "$.data.claimPublicId")
    }

    private fun statusOf(claimPublicId: String): ClaimStatus =
        tx.execute {
            em
                .createQuery("select c.status from Claim c where c.publicId = :publicId", ClaimStatus::class.java)
                .setParameter("publicId", claimPublicId)
                .singleResult
        }!!

    private fun claimCount(orderId: Long): Int =
        tx.execute {
            em
                .createQuery("select count(c) from Claim c where c.orderId = :orderId", Long::class.javaObjectType)
                .setParameter("orderId", orderId)
                .singleResult
                .toInt()
        }!!

    private fun claimItemCount(orderId: Long): Int =
        tx.execute {
            em
                .createQuery(
                    "select count(ci) from Claim c join c._items ci where c.orderId = :orderId",
                    Long::class.javaObjectType,
                ).setParameter("orderId", orderId)
                .singleResult
                .toInt()
        }!!

    private fun claimItemRefundTotal(orderId: Long): Long =
        tx.execute {
            em
                .createQuery(
                    "select sum(ci.refundAmount) from Claim c join c._items ci where c.orderId = :orderId",
                    Long::class.javaObjectType,
                ).setParameter("orderId", orderId)
                .singleResult ?: 0L
        }!!

    private fun claimScalar(
        orderId: Long,
        field: String,
    ): Long =
        tx.execute {
            em
                .createQuery("select c.$field from Claim c where c.orderId = :orderId", Long::class.javaObjectType)
                .setParameter("orderId", orderId)
                .singleResult
        }!!

    private data class ItemSpec(
        val unitPrice: Long,
        val quantity: Int,
    )

    private data class SeededOrder(
        val orderId: Long,
        val orderPublicId: String,
        val orderItemIds: List<Long>,
        val productAmount: Long,
        val shippingFee: Long,
        val finalPaymentAmount: Long,
    )

    /** 주문그룹 1건과 셀러 주문 1건(품목 N개)을 심는다. 품목마다 상품·버전을 따로 만들어 상품명을 구분한다. */
    private fun seedOrder(
        items: List<ItemSpec>,
        shippingFee: Long = 0L,
        usedPoint: Long = 0L,
        status: OrderStatus = OrderStatus.PAID,
    ): SeededOrder =
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
                    idempotencyKey = "claim-$buyerId-${sig++}",
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
            em
                .createQuery("update Order o set o.status = :status where o.id = :id")
                .setParameter("status", status)
                .setParameter("id", order.id)
                .executeUpdate()
            SeededOrder(
                orderId = order.id,
                orderPublicId = order.publicId,
                orderItemIds = order.items.map { it.id },
                productAmount = productAmount,
                shippingFee = shippingFee,
                finalPaymentAmount = group.finalPaymentAmount,
            )
        }!!

    private fun orderItemOf(
        index: Int,
        spec: ItemSpec,
    ): OrderItem {
        val productName = "멍냥 사료-$index"
        val category = Category.create(null, 1, "카테고리-${sig++}", null, 1)
        em.persist(category)
        val product = Product.register(category, sellerUserId, productName, null, null, spec.unitPrice, null, null, null)
        em.persist(product)
        val version =
            ProductVersion.snapshot(
                product = product,
                versionNo = 1,
                nameSnapshot = productName,
                priceSnapshot = spec.unitPrice,
                statusSnapshot = SaleStatus.ON_SALE,
                thumbnailKeySnapshot = "thumb-$index",
                changedBy = VersionChangedBy.SELLER,
            )
        em.persist(version)
        val combo = OptionCombination.create(product, "기본 / 1개", 0L, 100, "sig-${sig++}")
        em.persist(combo)
        em.flush()
        return OrderItem.of(
            productId = product.id,
            optionCombinationId = combo.id,
            optionNameSnapshot = "기본 / 1개",
            quantity = spec.quantity,
            unitPriceSnapshot = spec.unitPrice,
            discountAllocatedAmount = 0L,
            productVersionId = version.id,
        )
    }
}
