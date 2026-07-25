package com.aechak.api.product

import com.aechak.api.product.controller.ProductController
import com.aechak.application.product.port.ProductCatalogSort
import com.aechak.application.product.usecase.ProductUseCase
import com.aechak.application.product.usecase.query.ProductSearchQuery
import com.aechak.application.product.usecase.result.ProductOptionsResult
import com.aechak.application.product.usecase.result.ProductResult
import com.aechak.application.product.usecase.result.ProductSummaryResult
import com.aechak.application.support.CursorPageResult
import com.aechak.common.error.BusinessException
import com.aechak.domain.product.error.ProductErrorCode
import com.aechak.domain.product.product.enums.ProductImageType
import com.aechak.domain.product.product.enums.SaleStatus
import com.aechak.domain.user.user.enums.UserRole
import com.aechak.webcommon.error.GlobalExceptionHandler
import com.aechak.websecurity.authentication.AuthPrincipal
import org.hamcrest.CoreMatchers.nullValue
import org.junit.jupiter.api.AfterEach
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals

/** 계약 테스트 — GET /products·/{id}·/{id}/options의 파라미터 해석·형식 검증과 응답 JSON 모양을 고정한다(standalone MockMvc + fake UseCase). */
class ProductControllerTest {
    private var capturedQuery: ProductSearchQuery? = null
    private var stubbedResult: () -> CursorPageResult<ProductSummaryResult> = { pageOf(sampleCard()) }

    private var capturedDetailPublicId: String? = null
    private var capturedDetailUserId: Long? = null
    private var stubbedDetail: () -> ProductResult = { sampleDetail() }

    private var capturedOptionsPublicId: String? = null
    private var stubbedOptions: () -> ProductOptionsResult = { sampleOptions() }

    private val fakeUseCase =
        object : ProductUseCase {
            override fun getProducts(query: ProductSearchQuery): CursorPageResult<ProductSummaryResult> {
                capturedQuery = query
                return stubbedResult()
            }

            override fun getProduct(
                publicId: String,
                userId: Long?,
            ): ProductResult {
                capturedDetailPublicId = publicId
                capturedDetailUserId = userId
                return stubbedDetail()
            }

            override fun getProductOptions(publicId: String): ProductOptionsResult {
                capturedOptionsPublicId = publicId
                return stubbedOptions()
            }
        }

    private val mockMvc =
        MockMvcBuilders
            .standaloneSetup(ProductController(fakeUseCase))
            .setControllerAdvice(GlobalExceptionHandler())
            .setCustomArgumentResolvers(AuthenticationPrincipalArgumentResolver()) // @AuthenticationPrincipal 해석 — standalone은 직접 등록
            .build()

    @AfterEach
    fun clearSecurityContext() {
        SecurityContextHolder.clearContext()
    }

    private fun authenticateAs(userId: Long) {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(AuthPrincipal(userId, UserRole.GENERAL), null, emptyList())
    }

    private fun sampleCard(): ProductSummaryResult =
        ProductSummaryResult(
            productId = "01JABCDEFGHJKMNPQRSTVWXYZ0",
            name = "강아지 사료",
            sellerName = "멍멍상회",
            thumbnailImageKey = "products/1.jpg",
            regularPrice = 10000L,
            discountPrice = 7500L,
            discountRate = 25,
            saleStatus = SaleStatus.ON_SALE,
            averageRating = BigDecimal("4.5"),
            reviewCount = 12,
        )

    private fun pageOf(vararg cards: ProductSummaryResult): CursorPageResult<ProductSummaryResult> =
        CursorPageResult(items = cards.toList(), totalCount = cards.size.toLong(), nextCursor = null, hasNext = false)

    private fun sampleDetail(): ProductResult =
        ProductResult(
            productId = "01JABCDEFGHJKMNPQRSTVWXYZ0",
            name = "강아지 사료",
            description = "연어와 고구마가 들어간 사료",
            representativeImageKey = "products/1/rep.jpg",
            images =
                listOf(
                    ProductResult.ImageResult(ProductImageType.REPRESENTATIVE, "products/1/rep.jpg", 0),
                    ProductResult.ImageResult(ProductImageType.DETAIL, "products/1/d1.jpg", 1),
                ),
            regularPrice = 20000L,
            discountPrice = 14900L,
            discountRate = 26,
            saleStatus = SaleStatus.ON_SALE,
            shipping = ProductResult.ShippingResult(baseShippingFee = 3000L, freeShippingThreshold = 30000L),
            seller = ProductResult.SellerSummaryResult(storeName = "멍멍상회", profileImageKey = "sellers/77.jpg"),
            review = ProductResult.ReviewSummaryResult(reviewCount = 12, averageRating = BigDecimal("4.50")),
            categories =
                listOf(
                    ProductResult.CategoryResult(categoryId = 1L, name = "강아지", depth = 1),
                    ProductResult.CategoryResult(categoryId = 5L, name = "사료간식", depth = 2),
                ),
            isLiked = false,
        )

    private fun sampleOptions(): ProductOptionsResult =
        ProductOptionsResult(
            optionGroups =
                listOf(
                    ProductOptionsResult.OptionGroupResult(
                        optionGroupId = 1L,
                        name = "맛",
                        sortOrder = 1,
                        values =
                            listOf(
                                ProductOptionsResult.OptionValueResult(10L, "연어", 1),
                                ProductOptionsResult.OptionValueResult(11L, "치킨", 2),
                            ),
                    ),
                ),
            optionCombinations =
                listOf(
                    ProductOptionsResult.OptionCombinationResult(
                        optionCombinationId = 100L,
                        name = "연어",
                        additionalPrice = 0L,
                        optionValueIds = listOf(10L),
                        remainingStock = 3,
                        soldOut = false,
                    ),
                    ProductOptionsResult.OptionCombinationResult(
                        optionCombinationId = 101L,
                        name = "치킨",
                        additionalPrice = 1000L,
                        optionValueIds = listOf(11L),
                        remainingStock = null, // 임계치 초과 — 수량 비공개
                        soldOut = false,
                    ),
                    ProductOptionsResult.OptionCombinationResult(
                        optionCombinationId = 102L,
                        name = "품절맛",
                        additionalPrice = 0L,
                        optionValueIds = listOf(10L, 11L),
                        remainingStock = 0,
                        soldOut = true,
                    ),
                ),
        )

    // ---------- 목록 ----------

    @Test
    fun `상품 목록을 조회하면 200과 카드 필드, 페이지 정보를 반환한다`() {
        mockMvc
            .perform(get("/products"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.products[0].productId").value("01JABCDEFGHJKMNPQRSTVWXYZ0"))
            .andExpect(jsonPath("$.data.products[0].name").value("강아지 사료"))
            .andExpect(jsonPath("$.data.products[0].sellerName").value("멍멍상회"))
            .andExpect(jsonPath("$.data.products[0].thumbnailImageKey").value("products/1.jpg"))
            .andExpect(jsonPath("$.data.products[0].regularPrice").value(10000))
            .andExpect(jsonPath("$.data.products[0].discountPrice").value(7500))
            .andExpect(jsonPath("$.data.products[0].discountRate").value(25))
            .andExpect(jsonPath("$.data.products[0].saleStatus").value("ON_SALE"))
            .andExpect(jsonPath("$.data.products[0].averageRating").value(4.5))
            .andExpect(jsonPath("$.data.products[0].reviewCount").value(12))
            .andExpect(jsonPath("$.data.totalCount").value(1))
            .andExpect(jsonPath("$.data.hasNext").value(false))
    }

    @Test
    fun `파라미터 없이 호출하면 latest와 size 20 기본값으로 유스케이스에 전달한다`() {
        mockMvc.perform(get("/products")).andExpect(status().isOk)
        assertEquals(
            ProductSearchQuery(categoryId = null, sort = ProductCatalogSort.LATEST, cursor = null, size = 20),
            capturedQuery,
        )
    }

    @Test
    fun `요청 파라미터를 ProductSearchQuery로 변환해 유스케이스에 전달한다`() {
        mockMvc
            .perform(
                get("/products")
                    .param("category", "7")
                    .param("sort", "price_asc")
                    .param("cursor", "abc")
                    .param("size", "30"),
            ).andExpect(status().isOk)
        assertEquals(
            ProductSearchQuery(categoryId = 7L, sort = ProductCatalogSort.PRICE_ASC, cursor = "abc", size = 30),
            capturedQuery,
        )
    }

    @Test
    fun `sort가 허용되지 않은 값이면 400과 INVALID_REQUEST 코드로 응답한다`() {
        mockMvc
            .perform(get("/products").param("sort", "popular"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(90002))
    }

    @Test
    fun `size가 허용 범위를 벗어나면 400과 INVALID_REQUEST 코드로 응답한다`() {
        mockMvc
            .perform(get("/products").param("size", "0"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(90002))
        mockMvc
            .perform(get("/products").param("size", "101"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(90002))
    }

    @Test
    fun `category가 숫자가 아니면 400과 INVALID_REQUEST 코드로 응답한다`() {
        mockMvc
            .perform(get("/products").param("category", "abc"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(90002))
    }

    @Test
    fun `유스케이스가 카테고리 없음을 던지면 404와 CATEGORY_NOT_FOUND 코드로 응답한다`() {
        stubbedResult = { throw BusinessException(ProductErrorCode.CATEGORY_NOT_FOUND) }
        mockMvc
            .perform(get("/products").param("category", "999"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.errorCode").value(40012))
    }

    // ---------- 상세 ----------

    @Test
    fun `상품 상세를 조회하면 200과 상세 필드 전체를 반환한다`() {
        mockMvc
            .perform(get("/products/01JABCDEFGHJKMNPQRSTVWXYZ0"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.productId").value("01JABCDEFGHJKMNPQRSTVWXYZ0"))
            .andExpect(jsonPath("$.data.name").value("강아지 사료"))
            .andExpect(jsonPath("$.data.description").value("연어와 고구마가 들어간 사료"))
            .andExpect(jsonPath("$.data.representativeImageKey").value("products/1/rep.jpg"))
            .andExpect(jsonPath("$.data.images[0].imageType").value("REPRESENTATIVE"))
            .andExpect(jsonPath("$.data.images[1].imageType").value("DETAIL"))
            .andExpect(jsonPath("$.data.images[1].storageKey").value("products/1/d1.jpg"))
            .andExpect(jsonPath("$.data.images[1].sortOrder").value(1))
            .andExpect(jsonPath("$.data.regularPrice").value(20000))
            .andExpect(jsonPath("$.data.discountPrice").value(14900))
            .andExpect(jsonPath("$.data.discountRate").value(26))
            .andExpect(jsonPath("$.data.saleStatus").value("ON_SALE"))
            .andExpect(jsonPath("$.data.shipping.baseShippingFee").value(3000))
            .andExpect(jsonPath("$.data.shipping.freeShippingThreshold").value(30000))
            .andExpect(jsonPath("$.data.seller.storeName").value("멍멍상회"))
            .andExpect(jsonPath("$.data.seller.profileImageKey").value("sellers/77.jpg"))
            .andExpect(jsonPath("$.data.review.reviewCount").value(12))
            .andExpect(jsonPath("$.data.review.averageRating").value(4.50))
            .andExpect(jsonPath("$.data.categories[0].name").value("강아지"))
            .andExpect(jsonPath("$.data.categories[0].depth").value(1))
            .andExpect(jsonPath("$.data.categories[1].categoryId").value(5))
            .andExpect(jsonPath("$.data.categories[1].name").value("사료간식"))
            .andExpect(jsonPath("$.data.categories[1].depth").value(2))
            .andExpect(jsonPath("$.data.isLiked").value(false))
    }

    @Test
    fun `비로그인 상세 조회는 경로의 productId와 userId null을 유스케이스에 전달한다`() {
        mockMvc.perform(get("/products/01JABCDEFGHJKMNPQRSTVWXYZ0")).andExpect(status().isOk)
        assertEquals("01JABCDEFGHJKMNPQRSTVWXYZ0", capturedDetailPublicId)
        assertEquals(null, capturedDetailUserId)
    }

    @Test
    fun `인증된 상세 조회는 principal의 userId를 유스케이스에 전달한다`() {
        authenticateAs(42L)
        mockMvc.perform(get("/products/01JABCDEFGHJKMNPQRSTVWXYZ0")).andExpect(status().isOk)
        assertEquals(42L, capturedDetailUserId)
    }

    @Test
    fun `유스케이스가 상품 없음을 던지면 상세는 404와 PRODUCT_NOT_FOUND 코드로 응답한다`() {
        stubbedDetail = { throw BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND) }
        mockMvc
            .perform(get("/products/01JUNKNOWNXXXXXXXXXXXXXX00"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.errorCode").value(40001))
    }

    // ---------- 옵션 ----------

    @Test
    fun `옵션을 조회하면 200과 그룹, 조합 필드를 반환한다`() {
        mockMvc
            .perform(get("/products/01JABCDEFGHJKMNPQRSTVWXYZ0/options"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.optionGroups[0].optionGroupId").value(1))
            .andExpect(jsonPath("$.data.optionGroups[0].name").value("맛"))
            .andExpect(jsonPath("$.data.optionGroups[0].sortOrder").value(1))
            .andExpect(jsonPath("$.data.optionGroups[0].values[0].optionValueId").value(10))
            .andExpect(jsonPath("$.data.optionGroups[0].values[0].name").value("연어"))
            .andExpect(jsonPath("$.data.optionCombinations[0].optionCombinationId").value(100))
            .andExpect(jsonPath("$.data.optionCombinations[0].additionalPrice").value(0))
            .andExpect(jsonPath("$.data.optionCombinations[0].optionValueIds[0]").value(10))
            .andExpect(jsonPath("$.data.optionCombinations[0].remainingStock").value(3))
            .andExpect(jsonPath("$.data.optionCombinations[0].soldOut").value(false))
            .andExpect(jsonPath("$.data.optionCombinations[1].remainingStock").value(nullValue())) // 임계치 초과 — 비공개
            .andExpect(jsonPath("$.data.optionCombinations[2].remainingStock").value(0))
            .andExpect(jsonPath("$.data.optionCombinations[2].soldOut").value(true))
        assertEquals("01JABCDEFGHJKMNPQRSTVWXYZ0", capturedOptionsPublicId)
    }

    @Test
    fun `유스케이스가 상품 없음을 던지면 옵션도 404와 PRODUCT_NOT_FOUND 코드로 응답한다`() {
        stubbedOptions = { throw BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND) }
        mockMvc
            .perform(get("/products/01JUNKNOWNXXXXXXXXXXXXXX00/options"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.errorCode").value(40001))
    }
}
