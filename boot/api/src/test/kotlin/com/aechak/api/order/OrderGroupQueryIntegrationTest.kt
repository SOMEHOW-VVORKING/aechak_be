package com.aechak.api.order

import com.aechak.api.support.IntegrationTestBase
import com.aechak.application.pii.port.PiiCrypto
import com.aechak.domain.order.group.DeliveryAddressSnapshot
import com.aechak.domain.order.group.OrderGroup
import com.aechak.domain.order.order.Order
import com.aechak.domain.order.order.OrderItem
import com.aechak.domain.product.category.Category
import com.aechak.domain.product.option.OptionCombination
import com.aechak.domain.product.product.Product
import com.aechak.domain.product.product.enums.SaleStatus
import com.aechak.domain.product.version.ProductVersion
import com.aechak.domain.product.version.enums.VersionChangedBy
import com.jayway.jsonpath.JsonPath
import org.hibernate.SessionFactory
import org.hibernate.stat.Statistics
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
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
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.Base64
import java.util.UUID

/**
 * 주문그룹 단건 조회 API 통합. 이탈 복귀 화면이 쓰는 계약을 고정함. 절대 시각 만료, 복호된 배송지,
 * 셀러별 주문과 화면에 그릴 품목(상품명·썸네일·옵션명), 취소된 그룹의 200, 남의 그룹의 404 은닉.
 * 깨지면 결제창을 닫고 돌아온 구매자가 자기 주문 상태를 복원하지 못하거나, 남의 주문 실재가 새어 나감.
 */
class OrderGroupQueryIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var context: WebApplicationContext

    @Autowired
    private lateinit var securityFilterChain: FilterChainProxy

    @Autowired
    private lateinit var piiCrypto: PiiCrypto

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc =
            MockMvcBuilders
                .webAppContextSetup(context)
                .addFilters<DefaultMockMvcBuilder>(securityFilterChain)
                .build()
    }

    /** 셀러 하나가 파는 품목들 */
    private data class OrderSeed(
        val sellerId: Long,
        val sellerName: String,
        val shippingFee: Long,
        val items: List<ItemSeed>,
    )

    private data class ItemSeed(
        val productName: String,
        val optionName: String,
        val thumbnailKey: String,
        val quantity: Int,
        val unitPrice: Long,
    )

    private var sig = 0

    /**
     * 표시 필드는 product_versions와 option_combinations에 INNER JOIN으로 붙어 실물 행이 없으면 품목이 통째로 빠짐.
     * 그래서 카테고리부터 옵션 조합까지 실제로 심음.
     */
    private fun seedItem(
        sellerId: Long,
        item: ItemSeed,
    ): OrderItem {
        val tag = "s${sig++}"
        val listPrice = item.unitPrice + LIST_PRICE_GAP // 정가와 주문 단가를 어긋나게 심음. 같으면 단가를 product_versions에서 읽어도 단언이 통과함
        val category = Category.create(null, 1, "카테고리-$tag", null, 1)
        em.persist(category)
        val product = Product.register(category, sellerId, item.productName, null, null, listPrice, null, null, null)
        em.persist(product)
        val version =
            ProductVersion.snapshot(
                product = product,
                versionNo = 1,
                nameSnapshot = item.productName,
                priceSnapshot = listPrice,
                statusSnapshot = SaleStatus.ON_SALE,
                thumbnailKeySnapshot = item.thumbnailKey,
                changedBy = VersionChangedBy.SELLER,
            )
        em.persist(version)
        val combo = OptionCombination.create(product, item.optionName, 0L, 100, "sig-$tag")
        em.persist(combo)
        em.flush()
        return OrderItem.of(
            productId = product.id,
            optionCombinationId = combo.id,
            quantity = item.quantity,
            unitPriceSnapshot = item.unitPrice,
            discountAllocatedAmount = 0,
            productVersionId = version.id,
        )
    }

    /** 배송지 암호문은 실제 PiiCrypto로 만들어 복호 경로를 함께 태움 */
    private fun seedOrderGroup(
        buyerId: Long,
        orders: List<OrderSeed>,
        usedPoint: Long = 0L,
        expiresAt: LocalDateTime = EXPIRES_AT,
        cancelled: Boolean = false,
    ): String =
        tx.execute {
            val group =
                OrderGroup.create(
                    buyerId = buyerId,
                    deliveryAddressId = 1L,
                    deliveryAddress =
                        DeliveryAddressSnapshot(
                            receiverNameEnc = encrypt(RECEIVER_NAME),
                            contactNumberEnc = encrypt(CONTACT_NUMBER),
                            zipCode = "06236",
                            baseAddress = "서울 강남구 테헤란로 1",
                            detailAddress = "101동 202호",
                            deliveryMemo = null,
                        ),
                    usedPoint = usedPoint,
                    totalProductAmount = orders.sumOf { seed -> seed.items.sumOf { it.unitPrice * it.quantity } },
                    totalShippingFee = orders.sumOf { it.shippingFee },
                    idempotencyKey = "key-${UUID.randomUUID()}",
                    expiresAt = expiresAt,
                )
            if (cancelled) group.cancelUnpaid()
            em.persist(group)
            orders.forEach { seed ->
                em.persist(
                    Order.create(
                        orderGroup = group,
                        sellerId = seed.sellerId,
                        sellerNameSnapshot = seed.sellerName,
                        allocatedCouponDiscount = 0,
                        sellerShippingFee = seed.shippingFee,
                        items = seed.items.map { seedItem(seed.sellerId, it) },
                    ),
                )
            }
            em.flush()
            group.publicId
        }!!

    private fun encrypt(plain: String): String = Base64.getEncoder().encodeToString(piiCrypto.encrypt(plain))

    private fun orderPublicIdsOf(groupPublicId: String): List<String> =
        em
            .createQuery("select o.publicId from Order o where o.orderGroup.publicId = :publicId order by o.id", String::class.java)
            .setParameter("publicId", groupPublicId)
            .resultList

    private fun MockHttpServletRequestBuilder.bearer(token: String): MockHttpServletRequestBuilder =
        this.header(HttpHeaders.AUTHORIZATION, "Bearer $token")

    private fun getOrderGroup(
        publicId: String,
        token: String?,
    ): MockHttpServletRequestBuilder {
        val builder = get("/api/v1/order-groups/$publicId")
        return if (token != null) builder.bearer(token) else builder
    }

    private fun performOk(
        publicId: String,
        token: String,
    ): String =
        mockMvc
            .perform(getOrderGroup(publicId, token))
            .andExpect(status().isOk)
            .andReturn()
            .response
            .getContentAsString(Charsets.UTF_8)

    @Test
    fun `주문그룹을 조회하면 금액과 셀러별 주문, 복호된 배송지가 내려온다`() {
        val buyerId = createActiveUser()
        val publicId =
            seedOrderGroup(
                buyerId,
                orders =
                    listOf(
                        OrderSeed(3L, "포동상회", 3_000L, listOf(ItemSeed("강아지 사료 10kg", "닭고기 / 10kg", "thumb-feed", 2, 15_000L))),
                        OrderSeed(
                            4L,
                            "야옹샵",
                            2_500L,
                            listOf(
                                ItemSeed("고양이 자동 급식기", "화이트 / 5L", "thumb-feeder", 1, 8_000L),
                                ItemSeed("캣닢 스틱", "5개입", "thumb-catnip", 3, 1_000L),
                            ),
                        ),
                    ),
                usedPoint = 1_000L,
            )

        val body = performOk(publicId, mintAccessToken(buyerId))

        assertEquals(publicId, JsonPath.read<String>(body, "$.data.orderGroupId"), "요청한 주문그룹의 publicId를 그대로 돌려줘야 한다")
        assertEquals("PENDING_PAYMENT", JsonPath.read<String>(body, "$.data.status"), "결제 전 그룹은 PENDING_PAYMENT여야 한다")
        assertEquals(41_000, JsonPath.read<Int>(body, "$.data.totalProductAmount"), "상품금액은 전 셀러 라인 합계여야 한다")
        assertEquals(5_500, JsonPath.read<Int>(body, "$.data.totalShippingFee"), "배송비는 셀러별 배송비 합계여야 한다")
        assertEquals(1_000, JsonPath.read<Int>(body, "$.data.usedPoint"), "사용 적립금이 그대로 내려와야 한다")
        assertEquals(45_500, JsonPath.read<Int>(body, "$.data.finalPaymentAmount"), "최종 결제금액은 상품금액+배송비-적립금이어야 한다")

        val expiresAt = JsonPath.read<String?>(body, "$.data.expiresAt")
        assertNotNull(expiresAt, "만료 시각이 null이면 FE가 이 값과 현재 시각을 비교해 만료를 판정하지 못한다")
        assertEquals(
            EXPIRES_AT.atZone(ZoneId.systemDefault()).toOffsetDateTime(),
            OffsetDateTime.parse(expiresAt),
            "만료 시각은 FE가 남은 시간을 계산할 수 있게 오프셋이 붙은 절대 시각이어야 한다",
        )

        assertEquals(RECEIVER_NAME, JsonPath.read<String>(body, "$.data.deliveryAddress.receiverName"), "수령인명은 복호된 평문이어야 한다")
        assertEquals(CONTACT_NUMBER, JsonPath.read<String>(body, "$.data.deliveryAddress.contactNumber"), "연락처는 복호된 평문이어야 한다")
        assertEquals("06236", JsonPath.read<String>(body, "$.data.deliveryAddress.zipCode"), "우편번호 스냅샷이 내려와야 한다")
        assertEquals("서울 강남구 테헤란로 1", JsonPath.read<String>(body, "$.data.deliveryAddress.baseAddress"), "기본주소 스냅샷이 내려와야 한다")
        assertEquals("101동 202호", JsonPath.read<String>(body, "$.data.deliveryAddress.detailAddress"), "상세주소 스냅샷이 내려와야 한다")

        assertEquals(2, JsonPath.read<List<*>>(body, "$.data.orders").size, "셀러 둘이면 주문도 둘이어야 한다")
        assertEquals(
            listOf("포동상회", "야옹샵"),
            JsonPath.read<List<String>>(body, "$.data.orders[*].sellerName"),
            "주문은 셀러별로 갈리고 셀러명은 주문 시점 스냅샷이어야 한다",
        )
        assertEquals(
            orderPublicIdsOf(publicId),
            JsonPath.read<List<String>>(body, "$.data.orders[*].orderId"),
            "주문 식별자는 내부 id도 그룹 publicId도 아닌 그 주문 자신의 publicId여야 한다",
        )
        assertEquals("PENDING_PAYMENT", JsonPath.read<String>(body, "$.data.orders[0].status"), "결제 전 주문은 PENDING_PAYMENT여야 한다")
        assertEquals(3_000, JsonPath.read<Int>(body, "$.data.orders[0].sellerShippingFee"), "셀러별 배송비가 주문마다 내려와야 한다")

        assertEquals(2, JsonPath.read<List<*>>(body, "$.data.orders[1].items").size, "둘째 셀러의 품목 2건이 모두 내려와야 한다")
        val item = "$.data.orders[1].items[0]"
        assertEquals("고양이 자동 급식기", JsonPath.read<String>(body, "$item.productName"), "상품명이 없으면 화면이 무엇을 샀는지 못 그린다")
        assertEquals(
            "https://fake-cdn.local/thumb-feeder",
            JsonPath.read<String>(body, "$item.thumbnailUrl"),
            "썸네일은 저장 키가 아니라 바로 렌더할 수 있는 공개 URL이어야 한다",
        )
        assertEquals("화이트 / 5L", JsonPath.read<String>(body, "$item.optionName"), "같은 상품의 어느 옵션인지는 옵션명으로만 갈린다")
        assertEquals(1, JsonPath.read<Int>(body, "$item.quantity"), "주문 수량이 내려와야 한다")
        assertEquals(8_000, JsonPath.read<Int>(body, "$item.unitPrice"), "단가는 주문 시점 스냅샷이어야 한다")
        assertEquals("ORDERED", JsonPath.read<String>(body, "$item.itemStatus"), "품목별 상태가 내려와야 부분 취소·반품을 구분한다")

        assertFalse(body.contains("productVersionId"), "내부 상품 버전 id는 응답에 실리면 안 된다")
        assertFalse(body.contains("\"productId\""), "내부 상품 id는 응답에 실리면 안 된다")
        assertFalse(body.contains("optionCombinationId"), "품목을 상품명과 옵션명으로 식별하므로 내부 옵션조합 id는 응답에서 뺀다")
        assertFalse(body.contains("sellerId"), "셀러를 셀러명으로 표시하므로 내부 셀러 id는 응답에서 뺀다")
    }

    @Test
    fun `상품이 개명돼도 품목의 상품명은 주문 시점 그대로고 옵션명은 현재값을 따라간다`() {
        val buyerId = createActiveUser()
        val publicId =
            seedOrderGroup(
                buyerId,
                orders =
                    listOf(
                        OrderSeed(3L, "포동상회", 3_000L, listOf(ItemSeed("강아지 사료 10kg", "닭고기 / 10kg", "thumb-feed", 1, 15_000L))),
                    ),
            )
        renameAll(productName = "리뉴얼 사료 10kg", optionName = "소고기 / 10kg")

        val body = performOk(publicId, mintAccessToken(buyerId))

        val item = "$.data.orders[0].items[0]"
        assertEquals(
            "강아지 사료 10kg",
            JsonPath.read<String>(body, "$item.productName"),
            "상품명은 주문 시점 버전 스냅샷이라, 상품 현재 이름을 읽으면 지난 주문의 표기가 뒤늦게 바뀐다",
        )
        assertEquals(
            "소고기 / 10kg",
            JsonPath.read<String>(body, "$item.optionName"),
            "옵션명은 스냅샷 컬럼이 없어 현재값을 따라간다",
        )
    }

    /** 심는 상품이 하나뿐이라 where 없이 전부 갱신함 */
    private fun renameAll(
        productName: String,
        optionName: String,
    ) {
        tx.execute {
            em.createQuery("update Product p set p.name = :name").setParameter("name", productName).executeUpdate()
            em.createQuery("update OptionCombination oc set oc.name = :name").setParameter("name", optionName).executeUpdate()
        }
    }

    @Test
    fun `만료 취소된 주문그룹도 200으로 그대로 내려온다`() {
        val buyerId = createActiveUser()
        val publicId = seedOrderGroup(buyerId, orders = listOf(singleOrderSeed(3L)), cancelled = true)

        val body = performOk(publicId, mintAccessToken(buyerId))

        assertEquals("CANCELLED", JsonPath.read<String>(body, "$.data.status"), "취소된 그룹도 404가 아니라 CANCELLED 상태로 내려와야 한다")
        assertEquals(1, JsonPath.read<List<*>>(body, "$.data.orders").size, "취소된 그룹도 주문 내역을 그대로 담아야 한다")
    }

    @Test
    fun `없는 주문그룹 id는 404`() {
        val buyerId = createActiveUser()

        mockMvc
            .perform(getOrderGroup("01JZZZZZZZZZZZZZZZZZZZZZZZ", mintAccessToken(buyerId)))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.errorCode").value(50111))
    }

    @Test
    fun `남의 주문그룹은 403이 아니라 404로 숨긴다`() {
        val ownerId = createActiveUser()
        val publicId = seedOrderGroup(ownerId, orders = listOf(singleOrderSeed(3L)))
        val intruderId = createActiveUser()

        val response =
            mockMvc
                .perform(getOrderGroup(publicId, mintAccessToken(intruderId)))
                .andReturn()
                .response

        assertEquals(404, response.status, "남의 주문그룹은 403으로 실재를 알리지 않고 404로 숨겨야 한다")
        assertEquals(
            50111,
            JsonPath.read<Int>(response.getContentAsString(Charsets.UTF_8), "$.errorCode"),
            "없는 주문그룹과 같은 코드로 응답해 존재 여부가 갈리지 않아야 한다",
        )
    }

    @Test
    fun `토큰 없이 조회하면 401`() {
        val buyerId = createActiveUser()
        val publicId = seedOrderGroup(buyerId, orders = listOf(singleOrderSeed(3L)))

        mockMvc
            .perform(getOrderGroup(publicId, token = null))
            .andExpect(status().isUnauthorized)
    }

    /**
     * 절대 쿼리 수는 요청당 고정 쿼리 때문에 무관한 변경에도 흔들려서 증분만 봄.
     * 통계는 프로퍼티가 아니라 런타임에 켬. properties를 바꾸면 컨텍스트가 갈라져
     * 공유 MySQL 컨테이너가 조기 종료될 수 있음.
     */
    @Nested
    inner class DetailQueryCount {
        private lateinit var statistics: Statistics

        @BeforeEach
        fun enableStatistics() {
            statistics = em.entityManagerFactory.unwrap(SessionFactory::class.java).statistics
            statistics.isStatisticsEnabled = true
        }

        @AfterEach
        fun disableStatistics() {
            statistics.isStatisticsEnabled = false // SessionFactory는 컨텍스트가 공유해 안 끄면 뒤 테스트 클래스가 켜진 채로 돎
        }

        @Test
        fun `단건 조회 쿼리 수는 셀러별 주문 수와 무관하다`() {
            val buyerId = createActiveUser()
            val token = mintAccessToken(buyerId)
            val oneOrder = seedOrderGroup(buyerId, orders = listOf(singleOrderSeed(3L)))
            val threeOrders = seedOrderGroup(buyerId, orders = (3L..5L).map { singleOrderSeed(it) })

            val withOneOrder = countStatementsOnGet(oneOrder, token)
            val withThreeOrders = countStatementsOnGet(threeOrders, token)

            assertEquals(
                withOneOrder,
                withThreeOrders,
                "품목을 주문 id 묶음으로 한 번에 읽지 않으면 주문 수만큼 쿼리가 더 나간다 " +
                    "(1건=$withOneOrder, 3건=$withThreeOrders)",
            )
        }

        private fun countStatementsOnGet(
            publicId: String,
            token: String,
        ): Long {
            statistics.clear()
            performOk(publicId, token)
            return statistics.prepareStatementCount
        }
    }

    private fun singleOrderSeed(sellerId: Long): OrderSeed =
        OrderSeed(
            sellerId,
            "셀러-$sellerId",
            3_000L,
            listOf(ItemSeed("상품-$sellerId", "기본 옵션", "thumb-$sellerId", 1, 10_000L)),
        )

    companion object {
        private const val RECEIVER_NAME = "김재헌"
        private const val CONTACT_NUMBER = "01012345678"
        private const val LIST_PRICE_GAP = 1_000L

        // 오프셋 변환을 단언하려면 나노초까지 재현 가능해야 해서 고정값을 씀
        private val EXPIRES_AT: LocalDateTime = LocalDateTime.of(2026, 8, 26, 14, 10, 0)
    }
}
