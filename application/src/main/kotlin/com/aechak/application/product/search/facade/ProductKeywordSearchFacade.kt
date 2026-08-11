package com.aechak.application.product.search.facade

import com.aechak.application.product.search.service.ProductKeywordSearchService
import com.aechak.application.product.search.usecase.ProductKeywordSearchUseCase
import com.aechak.application.product.search.usecase.query.ProductKeywordSearchQuery
import com.aechak.application.product.usecase.result.ProductSummaryResult
import com.aechak.application.support.CursorPageResult
import com.aechak.domain.product.search.event.ProductKeywordSearchedEvent
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/** 상품 검색과 로그인 사용자의 검색 이벤트 발행 */
@Service
class ProductKeywordSearchFacade(
    private val productKeywordSearchService: ProductKeywordSearchService,
    private val eventPublisher: ApplicationEventPublisher,
) : ProductKeywordSearchUseCase {
    @Transactional(readOnly = true)
    override fun searchProducts(
        query: ProductKeywordSearchQuery,
        searcherId: Long?,
    ): CursorPageResult<ProductSummaryResult> {
        // 커서 앵커(밀리초)와 정밀도를 맞추려 첫 페이지부터 절삭
        val now = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS)
        val page = productKeywordSearchService.searchPage(query, now)
        val result =
            CursorPageResult(
                items = page.items.map { ProductSummaryResult.from(view = it, now = now) },
                totalCount = page.totalCount,
                nextCursor = page.nextCursor,
                hasNext = page.hasNext,
            )
        publishSearchedIfNeeded(query, searcherId)
        return result
    }

    /**
     * 로그인 사용자가 첫 페이지(cursor 없음)를 검색할 때만 검색 이벤트를 발행
     */
    private fun publishSearchedIfNeeded(
        query: ProductKeywordSearchQuery,
        searcherId: Long?,
    ) {
        if (searcherId == null || query.cursor != null) return
        eventPublisher.publishEvent(ProductKeywordSearchedEvent(searcherId, query.keyword))
    }
}
