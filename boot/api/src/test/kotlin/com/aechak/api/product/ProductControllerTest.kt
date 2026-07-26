package com.aechak.api.product

import com.aechak.api.product.controller.ProductController
import com.aechak.application.product.port.ProductCatalogSort
import com.aechak.application.product.usecase.ProductUseCase
import com.aechak.application.product.usecase.query.ProductSearchQuery
import com.aechak.application.product.usecase.result.ProductSummaryResult
import com.aechak.application.support.CursorPageResult
import com.aechak.common.error.BusinessException
import com.aechak.domain.product.error.ProductErrorCode
import com.aechak.domain.product.product.enums.SaleStatus
import com.aechak.webcommon.error.GlobalExceptionHandler
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals

/** 계약 테스트 — GET /products의 파라미터 해석·형식 검증과 응답 JSON 모양을 고정한다(standalone MockMvc + fake UseCase). */
class ProductControllerTest {
    private var capturedQuery: ProductSearchQuery? = null
    private var stubbedResult: () -> CursorPageResult<ProductSummaryResult> = { pageOf(sampleCard()) }

    private val fakeUseCase =
        object : ProductUseCase {
            override fun getProducts(query: ProductSearchQuery): CursorPageResult<ProductSummaryResult> {
                capturedQuery = query
                return stubbedResult()
            }
        }

    private val mockMvc =
        MockMvcBuilders
            .standaloneSetup(ProductController(fakeUseCase))
            .setControllerAdvice(GlobalExceptionHandler())
            .build()

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
}
