package com.aechak.api.order

import com.aechak.api.support.IntegrationTestBase
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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.security.web.FilterChainProxy
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.time.LocalDateTime

/**
 * 주문 목록·상세 조회 API 통합. HTTP 경계부터 실 MySQL까지 태워 결제대기 제외·타인 은닉·커서 경계 계약을 고정함.
 * 주문을 결제 전에 미리 만드는 모델이라 결제대기 행이 테이블에 섞여 있고, 그 행이 어느 응답으로도 새지 않는 것이 이 API의 핵심 계약임.
 */
class OrderQueryIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var context: WebApplicationContext

    @Autowired
    private lateinit var securityFilterChain: FilterChainProxy

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc =
            MockMvcBuilders
                .webAppContextSetup(context)
                .addFilters<DefaultMockMvcBuilder>(securityFilterChain)
                .build()
    }

    private var sig = 0

    private fun seedCombo(
        sellerUserId: Long,
        storeName: String,
        price: Long,
        productName: String,
    ): Pair<Long, Long> =
        tx.execute {
            val tag = "s${sig++}"
            em.persist(Seller.open(sellerUserId, storeName, 3000L))
            val category = Category.create(null, 1, "카테고리-$tag", null, 1)
            em.persist(category)
            val product = Product.register(category, sellerUserId, productName, null, null, price, null, null, null)
            em.persist(product)
            val version =
                ProductVersion.snapshot(
                    product = product,
                    versionNo = 1,
                    nameSnapshot = productName,
                    priceSnapshot = price,
                    statusSnapshot = SaleStatus.ON_SALE,
                    thumbnailKeySnapshot = "thumb-$tag",
                    changedBy = VersionChangedBy.SELLER,
                )
            em.persist(version)
            val combo = OptionCombination.create(product, "용량 5L / 화이트", 0L, 100, "sig-$tag")
            em.persist(combo)
            em.flush()
            combo.id to version.id
        }!!

    /** 주문그룹 하나와 그 아래 셀러 주문들을 심고 (그룹 publicId, 주문 publicId 목록)을 돌려줌. */
    private fun seedOrderGroup(
        buyerId: Long,
        sellers: List<SellerOrderSpec>,
        groupStatus: com.aechak.domain.order.group.enums.OrderGroupStatus,
    ): Pair<String, List<String>> =
        tx.execute {
            val productAmount = sellers.sumOf { it.unitPrice * it.quantity }
            val shippingFee = sellers.sumOf { it.shippingFee }
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
                    usedPoint = 0,
                    totalProductAmount = productAmount,
                    totalShippingFee = shippingFee,
                    idempotencyKey = "key-$buyerId-${sig++}",
                    expiresAt = LocalDateTime.now().plusMinutes(10),
                )
            if (groupStatus != com.aechak.domain.order.group.enums.OrderGroupStatus.PENDING_PAYMENT) {
                forceGroupStatus(group, groupStatus)
            }
            em.persist(group)
            val orderPublicIds =
                sellers.map { spec ->
                    val order =
                        Order.create(
                            orderGroup = group,
                            sellerId = spec.sellerId,
                            sellerNameSnapshot = spec.sellerName,
                            allocatedCouponDiscount = 0,
                            sellerShippingFee = spec.shippingFee,
                            items =
                                listOf(
                                    OrderItem.of(
                                        productId = spec.productId,
                                        optionCombinationId = spec.optionCombinationId,
                                        quantity = spec.quantity,
                                        unitPriceSnapshot = spec.unitPrice,
                                        discountAllocatedAmount = 0,
                                        productVersionId = spec.productVersionId,
                                    ),
                                ),
                        )
                    forceOrderStatus(order, spec.status)
                    em.persist(order)
                    order.publicId
                }
            em.flush()
            group.publicId to orderPublicIds
        }!!

    /** 상태 전이 API가 아직 없어 리플렉션으로 심음. 전이 규칙 자체는 이 테스트의 대상이 아님. */
    private fun forceOrderStatus(
        order: Order,
        status: OrderStatus,
    ) {
        val field = Order::class.java.getDeclaredField("status")
        field.isAccessible = true
        field.set(order, status)
    }

    private fun forceGroupStatus(
        group: OrderGroup,
        status: com.aechak.domain.order.group.enums.OrderGroupStatus,
    ) {
        val field = OrderGroup::class.java.getDeclaredField("status")
        field.isAccessible = true
        field.set(group, status)
    }

    private data class SellerOrderSpec(
        val sellerId: Long,
        val sellerName: String,
        val productId: Long,
        val optionCombinationId: Long,
        val productVersionId: Long,
        val unitPrice: Long,
        val quantity: Int,
        val shippingFee: Long,
        val status: OrderStatus,
    )

    private fun specOf(
        sellerId: Long,
        sellerName: String,
        productName: String,
        price: Long,
        status: OrderStatus,
        quantity: Int = 1,
    ): SellerOrderSpec {
        val (comboId, versionId) = seedCombo(sellerId, sellerName, price, productName)
        val productId =
            em
                .createQuery("select oc.product.id from OptionCombination oc where oc.id = :id", java.lang.Long::class.java)
                .setParameter("id", comboId)
                .singleResult
                .toLong()
        return SellerOrderSpec(sellerId, sellerName, productId, comboId, versionId, price, quantity, 3000L, status)
    }

    private fun MockHttpServletRequestBuilder.bearer(token: String): MockHttpServletRequestBuilder =
        this.header(HttpHeaders.AUTHORIZATION, "Bearer $token")

    private fun listBody(
        token: String,
        query: String = "",
    ): String =
        mockMvc
            .perform(get("/api/v1/orders$query").bearer(token))
            .andExpect(status().isOk)
            .andReturn()
            .response
            .getContentAsString(Charsets.UTF_8)

    @Test
    fun `결제대기 주문그룹은 목록에 나오지 않는다`() {
        val buyerId = createActiveUser()
        val token = mintAccessToken(buyerId)
        seedOrderGroup(
            buyerId,
            listOf(specOf(71L, "멍멍상회", "결제 안 한 급식기", 10000L, OrderStatus.PENDING_PAYMENT)),
            com.aechak.domain.order.group.enums.OrderGroupStatus.PENDING_PAYMENT,
        )

        mockMvc
            .perform(get("/api/v1/orders").bearer(token))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.orders").isEmpty)
            .andExpect(jsonPath("$.data.totalCount").value(0))
    }

    @Test
    fun `그룹에 결제대기 주문이 섞여 있어도 그 주문은 목록에 실리지 않는다`() {
        val buyerId = createActiveUser()
        val token = mintAccessToken(buyerId)
        seedOrderGroup(
            buyerId,
            listOf(
                specOf(71L, "냥이상점", "고양이 자동 급식기", 64000L, OrderStatus.SHIPPING),
                specOf(72L, "멍멍마트", "결제 안 한 사료", 20000L, OrderStatus.PENDING_PAYMENT),
            ),
            com.aechak.domain.order.group.enums.OrderGroupStatus.PAID,
        )

        val body = listBody(token)

        assertEquals(1, JsonPath.read<List<*>>(body, "$.data.orders").size, "배송중 주문이 있으니 그룹 자체는 목록에 남아야 한다")
        val sellers = JsonPath.read<List<String>>(body, "$.data.orders[0].sellerOrders[*].sellerName")
        assertEquals(1, sellers.size, "결제대기 셀러 주문은 카드 안에도 실리면 안 된다: $sellers")
        assertTrue(sellers.contains("냥이상점"), "결제가 끝난 셀러 주문만 남아야 한다: $sellers")
    }

    @Test
    fun `주문그룹 한 행 아래 셀러 주문들이 각자 상태를 달고 나온다`() {
        val buyerId = createActiveUser()
        val token = mintAccessToken(buyerId)
        seedOrderGroup(
            buyerId,
            listOf(
                specOf(71L, "냥이상점", "고양이 자동 급식기", 64000L, OrderStatus.SHIPPING),
                specOf(72L, "멍멍마트", "강아지 사료", 20000L, OrderStatus.PREPARING),
            ),
            com.aechak.domain.order.group.enums.OrderGroupStatus.PAID,
        )

        val body = listBody(token)

        assertEquals(1, JsonPath.read<List<*>>(body, "$.data.orders").size, "주문그룹 하나가 한 행이어야 한다")
        val statuses = JsonPath.read<List<String>>(body, "$.data.orders[0].sellerOrders[*].status")
        assertTrue(statuses.containsAll(listOf("SHIPPING", "PREPARING")), "셀러별 상태가 그대로 나와야 한다: $statuses")
        assertEquals(84000, JsonPath.read<Int>(body, "$.data.orders[0].totalProductAmount"), "그룹 상품 금액은 두 셀러 합계여야 한다")
    }

    @Test
    fun `목록의 대표 품목은 주문 시점 상품명과 현재 옵션명을 함께 싣는다`() {
        val buyerId = createActiveUser()
        val token = mintAccessToken(buyerId)
        seedOrderGroup(
            buyerId,
            listOf(specOf(71L, "냥이상점", "고양이 자동 급식기 스마트 5L", 64000L, OrderStatus.DELIVERED)),
            com.aechak.domain.order.group.enums.OrderGroupStatus.PAID,
        )

        val body = listBody(token)

        val item = "$.data.orders[0].sellerOrders[0].representativeItem"
        assertEquals("고양이 자동 급식기 스마트 5L", JsonPath.read<String>(body, "$item.productName"), "상품명은 주문 시점 버전 스냅샷이어야 한다")
        assertEquals("용량 5L / 화이트", JsonPath.read<String>(body, "$item.optionName"), "옵션명은 옵션조합 현재값이어야 한다")
        assertEquals(64000, JsonPath.read<Int>(body, "$item.unitPrice"), "단가는 주문 시점 스냅샷이어야 한다")
        assertEquals(1, JsonPath.read<Int>(body, "$.data.orders[0].sellerOrders[0].itemCount"), "품목 종류 수가 맞아야 한다")
    }

    @Test
    fun `남의 주문은 목록에 섞이지 않는다`() {
        val mine = createActiveUser()
        val other = createActiveUser()
        val token = mintAccessToken(mine)
        seedOrderGroup(
            other,
            listOf(specOf(71L, "남의상점", "남의 상품", 5000L, OrderStatus.PAID)),
            com.aechak.domain.order.group.enums.OrderGroupStatus.PAID,
        )

        mockMvc
            .perform(get("/api/v1/orders").bearer(token))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.orders").isEmpty)
    }

    @Test
    fun `진행중 필터는 배송완료와 취소를 걸러낸다`() {
        val buyerId = createActiveUser()
        val token = mintAccessToken(buyerId)
        seedOrderGroup(
            buyerId,
            listOf(specOf(71L, "가게1", "배송중 상품", 1000L, OrderStatus.SHIPPING)),
            com.aechak.domain.order.group.enums.OrderGroupStatus.PAID,
        )
        seedOrderGroup(
            buyerId,
            listOf(specOf(72L, "가게2", "배송완료 상품", 1000L, OrderStatus.DELIVERED)),
            com.aechak.domain.order.group.enums.OrderGroupStatus.PAID,
        )
        seedOrderGroup(
            buyerId,
            listOf(specOf(73L, "가게3", "취소 상품", 1000L, OrderStatus.CANCELLED)),
            com.aechak.domain.order.group.enums.OrderGroupStatus.CANCELLED,
        )

        val ongoing = listBody(token, "?status=ongoing")
        val completed = listBody(token, "?status=completed")
        val cancelled = listBody(token, "?status=cancelled")
        val all = listBody(token, "?status=all")

        assertEquals(1, JsonPath.read<List<*>>(ongoing, "$.data.orders").size, "진행중은 배송중 하나여야 한다")
        assertEquals(1, JsonPath.read<List<*>>(completed, "$.data.orders").size, "완료는 배송완료 하나여야 한다")
        assertEquals(1, JsonPath.read<List<*>>(cancelled, "$.data.orders").size, "취소는 취소 하나여야 한다")
        assertEquals(3, JsonPath.read<List<*>>(all, "$.data.orders").size, "전체는 셋 다여야 한다")
    }

    @Test
    fun `커서로 끝까지 넘기면 중복 없이 전부 돌고 마지막 페이지에서 멈춘다`() {
        val buyerId = createActiveUser()
        val token = mintAccessToken(buyerId)
        repeat(5) { i ->
            seedOrderGroup(
                buyerId,
                listOf(specOf(80L + i, "가게$i", "상품$i", 1000L, OrderStatus.PAID)),
                com.aechak.domain.order.group.enums.OrderGroupStatus.PAID,
            )
        }

        val seen = mutableListOf<String>()
        var cursor: String? = null
        var pages = 0
        do {
            val query = if (cursor == null) "?size=2" else "?size=2&cursor=$cursor"
            val body = listBody(token, query)
            seen += JsonPath.read<List<String>>(body, "$.data.orders[*].orderGroupId")
            cursor = JsonPath.read<String?>(body, "$.data.nextCursor")
            pages++
        } while (cursor != null && pages < 10)

        assertEquals(5, seen.size, "5건을 전부 돌아야 한다")
        assertEquals(5, seen.toSet().size, "중복 없이 돌아야 한다")
        assertEquals(3, pages, "2건씩이면 3페이지여야 한다")
    }

    @Test
    fun `첫 페이지만 totalCount를 싣고 다음 페이지는 비운다`() {
        val buyerId = createActiveUser()
        val token = mintAccessToken(buyerId)
        repeat(3) { i ->
            seedOrderGroup(
                buyerId,
                listOf(specOf(90L + i, "가게$i", "상품$i", 1000L, OrderStatus.PAID)),
                com.aechak.domain.order.group.enums.OrderGroupStatus.PAID,
            )
        }

        val first = listBody(token, "?size=2")
        val cursor = JsonPath.read<String>(first, "$.data.nextCursor")
        val second = listBody(token, "?size=2&cursor=$cursor")

        assertEquals(3, JsonPath.read<Int>(first, "$.data.totalCount"), "첫 페이지는 총건수를 싣는다")
        assertNull(JsonPath.parse(second).read<Any?>("$.data.totalCount"), "이후 페이지는 비운다")
    }

    @Test
    fun `주문이 없으면 빈 목록과 hasNext false를 돌려준다`() {
        val token = mintAccessToken(createActiveUser())

        mockMvc
            .perform(get("/api/v1/orders").bearer(token))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.orders").isEmpty)
            .andExpect(jsonPath("$.data.hasNext").value(false))
            .andExpect(jsonPath("$.data.nextCursor").doesNotExist())
    }

    @Test
    fun `페이지 중간에 필터를 바꾼 커서는 90002로 거절한다`() {
        val buyerId = createActiveUser()
        val token = mintAccessToken(buyerId)
        repeat(3) { i ->
            seedOrderGroup(
                buyerId,
                listOf(specOf(60L + i, "가게$i", "상품$i", 1000L, OrderStatus.PAID)),
                com.aechak.domain.order.group.enums.OrderGroupStatus.PAID,
            )
        }
        val cursor = JsonPath.read<String>(listBody(token, "?status=all&size=1"), "$.data.nextCursor")

        mockMvc
            .perform(get("/api/v1/orders?status=ongoing&size=1&cursor=$cursor").bearer(token))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(90002))
    }

    @Test
    fun `깨진 커서는 90002로 거절한다`() {
        val token = mintAccessToken(createActiveUser())

        mockMvc
            .perform(get("/api/v1/orders?cursor=%25%25broken").bearer(token))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(90002))
    }

    @Test
    fun `알 수 없는 status 값은 90001로 거절한다`() {
        val token = mintAccessToken(createActiveUser())

        mockMvc
            .perform(get("/api/v1/orders?status=nope").bearer(token))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(90001))
    }

    @Test
    fun `size가 상한을 넘으면 90001로 거절한다`() {
        val token = mintAccessToken(createActiveUser())

        mockMvc
            .perform(get("/api/v1/orders?size=101").bearer(token))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(90001))
    }

    @Test
    fun `상세는 주문 시점 품목과 결제 금액을 셀러 배송비와 함께 돌려준다`() {
        val buyerId = createActiveUser()
        val token = mintAccessToken(buyerId)
        val (groupPublicId, orderPublicIds) =
            seedOrderGroup(
                buyerId,
                listOf(specOf(71L, "냥이상점", "고양이 자동 급식기 스마트 5L", 64000L, OrderStatus.SHIPPING)),
                com.aechak.domain.order.group.enums.OrderGroupStatus.PAID,
            )

        mockMvc
            .perform(get("/api/v1/orders/${orderPublicIds.first()}").bearer(token))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.orderId").value(orderPublicIds.first()))
            .andExpect(jsonPath("$.data.orderGroupId").value(groupPublicId))
            .andExpect(jsonPath("$.data.status").value("SHIPPING"))
            .andExpect(jsonPath("$.data.sellerName").value("냥이상점"))
            .andExpect(jsonPath("$.data.items[0].productName").value("고양이 자동 급식기 스마트 5L"))
            .andExpect(jsonPath("$.data.items[0].optionName").value("용량 5L / 화이트"))
            .andExpect(jsonPath("$.data.items[0].unitPrice").value(64000))
            .andExpect(jsonPath("$.data.payment.sellerShippingFee").value(3000))
            .andExpect(jsonPath("$.data.payment.totalProductAmount").value(64000))
            .andExpect(jsonPath("$.data.payment.finalPaymentAmount").value(67000))
    }

    @Test
    fun `상세 응답에 내부 숫자 id가 실리지 않는다`() {
        val buyerId = createActiveUser()
        val token = mintAccessToken(buyerId)
        val (_, orderPublicIds) =
            seedOrderGroup(
                buyerId,
                listOf(specOf(71L, "냥이상점", "급식기", 1000L, OrderStatus.PAID)),
                com.aechak.domain.order.group.enums.OrderGroupStatus.PAID,
            )

        val body =
            mockMvc
                .perform(get("/api/v1/orders/${orderPublicIds.first()}").bearer(token))
                .andExpect(status().isOk)
                .andReturn()
                .response
                .getContentAsString(Charsets.UTF_8)

        assertEquals(26, JsonPath.read<String>(body, "$.data.orderId").length, "주문번호는 ULID 26자여야 한다")
        assertTrue(!body.contains("\"id\""), "내부 id 필드가 응답에 있으면 안 된다: $body")
    }

    @Test
    fun `남의 주문 상세는 50000으로 숨긴다`() {
        val mine = createActiveUser()
        val other = createActiveUser()
        val token = mintAccessToken(mine)
        val (_, orderPublicIds) =
            seedOrderGroup(
                other,
                listOf(specOf(71L, "남의상점", "남의 상품", 5000L, OrderStatus.PAID)),
                com.aechak.domain.order.group.enums.OrderGroupStatus.PAID,
            )

        mockMvc
            .perform(get("/api/v1/orders/${orderPublicIds.first()}").bearer(token))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.errorCode").value(50000))
    }

    @Test
    fun `결제대기 주문 상세는 본인 것이어도 50000으로 숨긴다`() {
        val buyerId = createActiveUser()
        val token = mintAccessToken(buyerId)
        val (_, orderPublicIds) =
            seedOrderGroup(
                buyerId,
                listOf(specOf(71L, "멍멍상회", "결제 안 한 상품", 10000L, OrderStatus.PENDING_PAYMENT)),
                com.aechak.domain.order.group.enums.OrderGroupStatus.PENDING_PAYMENT,
            )

        mockMvc
            .perform(get("/api/v1/orders/${orderPublicIds.first()}").bearer(token))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.errorCode").value(50000))
    }

    @Test
    fun `토큰 없이 목록을 부르면 401이다`() {
        mockMvc
            .perform(get("/api/v1/orders"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.errorCode").value(20004))
    }

    @Test
    fun `토큰 없이 상세를 부르면 401이다`() {
        mockMvc
            .perform(get("/api/v1/orders/01JXFYK3S9GQ4T7VBW2N8DHMCE"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.errorCode").value(20004))
    }

    @Test
    fun `없는 주문번호는 50000으로 거절한다`() {
        val token = mintAccessToken(createActiveUser())

        mockMvc
            .perform(get("/api/v1/orders/01JXFYK3S9GQ4T7VBW2N8DHMCE").bearer(token))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.errorCode").value(50000))
    }
}
