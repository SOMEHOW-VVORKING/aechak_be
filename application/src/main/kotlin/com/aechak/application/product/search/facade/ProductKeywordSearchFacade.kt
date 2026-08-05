package com.aechak.application.product.search.facade

import com.aechak.application.product.search.service.ProductKeywordSearchService
import com.aechak.application.product.search.usecase.ProductKeywordSearchUseCase
import com.aechak.application.product.search.usecase.query.ProductKeywordSearchQuery
import com.aechak.application.product.usecase.result.ProductSummaryResult
import com.aechak.application.support.CursorPageResult
import com.aechak.domain.product.stats.repository.ProductStatsRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/** 검색 결과에 상품 통계 추가 */
@Service
class ProductKeywordSearchFacade(
    private val productKeywordSearchService: ProductKeywordSearchService,
    private val productStatsRepository: ProductStatsRepository,
) : ProductKeywordSearchUseCase {
    @Transactional(readOnly = true)
    override fun searchProducts(query: ProductKeywordSearchQuery): CursorPageResult<ProductSummaryResult> {
        val now = LocalDateTime.now()
        val page = productKeywordSearchService.searchPage(query, now)
        val statsById =
            productStatsRepository.findAllByProductIds(page.items.map { it.id }).associateBy { it.productId }
        return CursorPageResult(
            items = page.items.map { ProductSummaryResult.from(view = it, stats = statsById[it.id], now = now) },
            totalCount = page.totalCount,
            nextCursor = page.nextCursor,
            hasNext = page.hasNext,
        )
    }
}
