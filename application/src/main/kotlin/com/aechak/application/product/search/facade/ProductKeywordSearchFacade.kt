package com.aechak.application.product.search.facade

import com.aechak.application.product.like.service.ProductLikeStatusService
import com.aechak.application.product.search.service.ProductKeywordSearchService
import com.aechak.application.product.search.usecase.ProductKeywordSearchUseCase
import com.aechak.application.product.search.usecase.query.ProductKeywordSearchQuery
import com.aechak.application.product.stats.service.ProductStatsService
import com.aechak.application.product.usecase.result.ProductSummaryResult
import com.aechak.application.support.CursorPageResult
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/** 검색 결과에 상품 통계 추가 */
@Service
class ProductKeywordSearchFacade(
    private val productKeywordSearchService: ProductKeywordSearchService,
    private val productStatsService: ProductStatsService,
    private val productLikeStatusService: ProductLikeStatusService,
) : ProductKeywordSearchUseCase {
    @Transactional(readOnly = true)
    override fun searchProducts(
        query: ProductKeywordSearchQuery,
        userId: Long?,
    ): CursorPageResult<ProductSummaryResult> {
        val now = LocalDateTime.now()
        val page = productKeywordSearchService.searchPage(query, now)
        val statsById = productStatsService.getStatsByProductIds(page.items.map { it.id })
        val likedIds = productLikeStatusService.likedProductIds(userId, page.items.map { it.id })
        return ProductSummaryResult.fromPage(page, statsById, likedIds, now)
    }
}
