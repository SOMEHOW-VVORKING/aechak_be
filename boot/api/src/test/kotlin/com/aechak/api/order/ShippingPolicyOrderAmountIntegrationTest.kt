package com.aechak.api.order

import com.aechak.api.support.IntegrationTestBase
import com.aechak.application.seller.usecase.SellerShippingPolicyUseCase
import com.aechak.application.seller.usecase.command.UpdateShippingPolicyCommand
import com.aechak.domain.order.cart.Cart
import com.aechak.domain.product.category.Category
import com.aechak.domain.product.option.OptionCombination
import com.aechak.domain.product.product.Product
import com.aechak.domain.product.product.enums.SaleStatus
import com.aechak.domain.product.version.ProductVersion
import com.aechak.domain.product.version.enums.VersionChangedBy
import com.aechak.domain.seller.seller.Seller
import com.aechak.domain.user.address.DeliveryAddress
import com.aechak.domain.user.user.User
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.web.FilterChainProxy
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

/**
 * 셀러 배송 정책 저장이 구매자 금액에 반영되는지 보는 회귀. 배송 정책 화면이 쓰는 기본 배송비·무료배송 임계는
 * 장바구니 표시와 주문 금액 계산이 읽는 컬럼과 같은 컬럼이다.
 * 깨지면 셀러가 설정한 값이 구매자 금액에 닿지 않거나(새 컬럼으로 갈라짐), 반대로 기존 필드 의미가 바뀐 것이다.
 */
class ShippingPolicyOrderAmountIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var context: WebApplicationContext

    @Autowired
    private lateinit var securityFilterChain: FilterChainProxy

    @Autowired
    private lateinit var sellerShippingPolicyUseCase: SellerShippingPolicyUseCase

    private lateinit var mockMvc: MockMvc

    private val sellerUserId = 71L

    @BeforeEach
    fun setUp() {
        mockMvc =
            MockMvcBuilders
                .webAppContextSetup(context)
                .addFilters<DefaultMockMvcBuilder>(securityFilterChain)
                .build()
    }

    @Test
    fun `기본 배송비를 올리면 주문 금액도 함께 오른다`() {
        val buyerId = createActiveUser()
        val token = mintAccessToken(buyerId)
        val combos = seedSellerProduct(baseShippingFee = 3000L, price = 19_000L, comboCount = 3)
        val addressId = seedAddress(buyerId)

        // 정책 저장 전 — 19000 + 3000
        mockMvc
            .perform(postOrderGroup(token, seedCart(buyerId, combos[0]), addressId, 22_000L, "before"))
            .andExpect(status().isCreated)

        savePolicy(baseShippingFee = 5000L, freeShippingThreshold = 100_000L)

        // 옛 금액으로 보내면 서버 계산과 어긋나 거절된다
        mockMvc
            .perform(postOrderGroup(token, seedCart(buyerId, combos[1]), addressId, 22_000L, "stale"))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.errorCode").value(50106))

        // 새 배송비로 보내면 통과한다 — 19000 + 5000
        mockMvc
            .perform(postOrderGroup(token, seedCart(buyerId, combos[2]), addressId, 24_000L, "after"))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.data.totalShippingFee").value(5000))
            .andExpect(jsonPath("$.data.finalPaymentAmount").value(24_000))
    }

    @Test
    fun `무료배송 임계를 상품금액 아래로 내리면 배송비가 0이 된다`() {
        val buyerId = createActiveUser()
        val token = mintAccessToken(buyerId)
        val combos = seedSellerProduct(baseShippingFee = 3000L, price = 19_000L)
        val addressId = seedAddress(buyerId)

        savePolicy(baseShippingFee = 3000L, freeShippingThreshold = 10_000L)

        mockMvc
            .perform(postOrderGroup(token, seedCart(buyerId, combos[0]), addressId, 19_000L, "free"))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.data.totalShippingFee").value(0))
    }

    @Test
    fun `저장한 배송 정책이 장바구니 응답의 셀러 그룹에 그대로 보인다`() {
        val buyerId = createActiveUser()
        val token = mintAccessToken(buyerId)
        val combos = seedSellerProduct(baseShippingFee = 3000L, price = 19_000L)
        seedCart(buyerId, combos[0])

        savePolicy(baseShippingFee = 4500L, freeShippingThreshold = 70_000L)

        mockMvc
            .perform(get("/api/v1/carts").header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.sellerGroups[0].baseShippingFee").value(4500))
            .andExpect(jsonPath("$.data.sellerGroups[0].freeShippingThreshold").value(70_000))
    }

    // ---------- 헬퍼 ----------

    private fun savePolicy(
        baseShippingFee: Long,
        freeShippingThreshold: Long?,
    ) {
        sellerShippingPolicyUseCase.updateShippingPolicy(
            UpdateShippingPolicyCommand(
                sellerId = sellerUserId,
                defaultCourierCode = null,
                baseShippingFee = baseShippingFee,
                freeShippingThreshold = freeShippingThreshold,
                originZipCode = "06236",
                originRoadAddress = "서울 강남구 테헤란로 1",
                originDetailAddress = "3층",
                returnSameAsOrigin = true,
                returnZipCode = null,
                returnRoadAddress = null,
                returnDetailAddress = null,
                jejuExtraFee = 3000L,
                remoteAreaExtraFee = 5000L,
                returnFee = 5000L,
                exchangeFee = 10_000L,
                avgDispatchDays = 2,
                shippingPolicyText = null,
            ),
        )
    }

    /** 셀러 + 상품 + 옵션조합(+버전)을 심고 옵션조합 id 목록을 돌려준다. */
    private fun seedSellerProduct(
        baseShippingFee: Long,
        price: Long,
        comboCount: Int = 1,
    ): List<Long> =
        tx.execute {
            em.persist(Seller.open(sellerUserId, "멍멍상회", baseShippingFee))
            val category = Category.create(null, 1, "강아지", null, 1)
            em.persist(category)
            val product = Product.register(category, sellerUserId, "강아지 사료 1kg", null, null, price, null, null, null)
            em.persist(product)
            em.persist(
                ProductVersion.snapshot(
                    product = product,
                    versionNo = 1,
                    nameSnapshot = product.name,
                    priceSnapshot = price,
                    statusSnapshot = SaleStatus.ON_SALE,
                    thumbnailKeySnapshot = "thumb-1",
                    changedBy = VersionChangedBy.SELLER,
                ),
            )
            (1..comboCount).map { i ->
                val combo = OptionCombination.create(product, "기본 / ${i}개입", 0L, 100, "sig-$i")
                em.persist(combo)
                combo.id
            }
        }!!

    /** 수량 1짜리 장바구니 1줄. 같은 옵션을 다시 담으면 수량이 합쳐지므로 요청마다 다른 옵션조합을 쓴다. */
    private fun seedCart(
        buyerId: Long,
        comboId: Long,
    ): List<Long> =
        tx.execute {
            val cart =
                em
                    .createQuery("select c from Cart c where c.buyerId = :b", Cart::class.java)
                    .setParameter("b", buyerId)
                    .resultList
                    .firstOrNull()
                    ?: Cart.create(buyerId).also { em.persist(it) }
            val item = cart.addItem(comboId, 1)
            em.flush()
            listOf(item.id)
        }!!

    private fun seedAddress(userId: Long): Long =
        tx.execute {
            val address =
                DeliveryAddress.register(
                    user = em.find(User::class.java, userId),
                    receiverName = "홍길동",
                    contactNumber = "01012345678".toByteArray(Charsets.UTF_8),
                    zipCode = "12345",
                    baseAddress = "서울시 애착구 멍냥로 1",
                    detailAddress = "101동 202호",
                    isDefault = true,
                )
            em.persist(address)
            address.id
        }!!

    private fun postOrderGroup(
        token: String,
        cartItemIds: List<Long>,
        addressId: Long,
        expectedFinalAmount: Long,
        idempotencyKey: String,
    ): MockHttpServletRequestBuilder =
        post("/api/v1/order-groups")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .header("Idempotency-Key", idempotencyKey)
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {"cartItemIds": $cartItemIds, "deliveryAddressId": $addressId,
                 "usedPoint": 0, "expectedFinalAmount": $expectedFinalAmount}
                """.trimIndent(),
            )
}
