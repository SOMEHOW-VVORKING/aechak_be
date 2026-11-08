package com.aechak.application.product.search.service

import com.aechak.application.product.product.port.view.ProductCatalogView
import com.aechak.application.product.search.port.ProductKeywordFilter
import com.aechak.application.product.search.port.ProductKeywordSearchCondition
import com.aechak.application.product.search.port.ProductKeywordSearchFirstPage
import com.aechak.application.product.search.port.ProductKeywordSearchPort
import com.aechak.application.product.search.port.ProductKeywordSearchSort
import com.aechak.application.product.search.support.ProductKeywordSearchCursorCodec
import com.aechak.application.product.search.usecase.query.ProductKeywordSearchQuery
import com.aechak.common.error.BusinessException
import com.aechak.domain.product.category.Category
import com.aechak.domain.product.category.repository.CategoryRepository
import com.aechak.domain.product.error.ProductErrorCode
import org.springframework.dao.QueryTimeoutException
import org.springframework.dao.TransientDataAccessResourceException
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * 계약: 검색 엔진의 일시적 실패(응답 대기 상한 초과, 동시 요청 상한 초과, 연결 실패)가
 * 다음 페이지 커서를 해석할 때도 첫 페이지를 검색할 때도 검색 지연 오류(503)로 끝나는지
 */
class ProductKeywordSearchServiceTest {
    private val now = LocalDateTime.of(2026, 11, 7, 12, 0)

    @Test
    fun `커서 해석이 응답 대기 상한을 넘기면 검색 지연 오류로 바꾼다`() {
        val service = ProductKeywordSearchService(FailingPort(QueryTimeoutException("검색 엔진 응답 시간 초과")), NoCategories)
        val filter = ProductKeywordFilter("강아지", null, null, null, null, freeShipping = false, excludeSoldOut = false)
        val cursor =
            ProductKeywordSearchCursorCodec.encode(
                sort = ProductKeywordSearchSort.LATEST,
                filterHash = ProductKeywordSearchCursorCodec.filterHash(filter),
                publicId = "01JD5G7HJK8M2N4PQ6RSTV9WXY",
                lastReviewCount = null,
                lastPrice = null,
                now = now,
            )
        val query = ProductKeywordSearchQuery(keyword = "강아지", sort = ProductKeywordSearchSort.LATEST, cursor = cursor)

        val error = assertFailsWith<BusinessException> { service.searchPage(query, now, categoryIds = setOf(1L)) }

        assertEquals(ProductErrorCode.PRODUCT_SEARCH_TIMEOUT, error.errorCode)
    }

    @Test
    fun `동시 요청 상한 초과나 연결 실패도 검색 지연 오류로 바꾼다`() {
        val service = ProductKeywordSearchService(FailingPort(TransientDataAccessResourceException("검색 엔진 연결 실패")), NoCategories)

        val error = assertFailsWith<BusinessException> { service.searchPage(ProductKeywordSearchQuery(keyword = "강아지"), now, setOf(1L)) }

        assertEquals(ProductErrorCode.PRODUCT_SEARCH_TIMEOUT, error.errorCode)
    }

    /** 모든 요청에서 정한 예외를 던지는 검색 포트 */
    private class FailingPort(
        private val failure: RuntimeException,
    ) : ProductKeywordSearchPort {
        override val readsDatabase = false

        override fun search(condition: ProductKeywordSearchCondition): List<ProductCatalogView> = throw failure

        override fun searchFirstPage(condition: ProductKeywordSearchCondition): ProductKeywordSearchFirstPage = throw failure

        override fun findIdByPublicId(publicId: String): Long? = throw failure
    }

    private object NoCategories : CategoryRepository {
        override fun findActiveById(id: Long): Category? = null

        override fun findAllActiveOrderedBySortOrder(): List<Category> = emptyList()
    }
}
