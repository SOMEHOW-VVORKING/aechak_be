package com.aechak.application.product.search.facade

import com.aechak.application.product.like.service.ProductLikeStatusService
import com.aechak.application.product.product.usecase.result.ProductSummaryResult
import com.aechak.application.product.search.service.ProductKeywordSearchService
import com.aechak.application.product.search.usecase.ProductKeywordSearchUseCase
import com.aechak.application.product.search.usecase.query.ProductKeywordSearchQuery
import com.aechak.application.support.CursorPageResult
import com.aechak.domain.product.search.event.ProductKeywordSearchedEvent
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * 상품 검색과 로그인 사용자의 검색 이벤트 발행
 * DB를 읽는 검색은 읽기 트랜잭션 하나로 묶고, DB를 읽지 않는 검색은 트랜잭션 밖에서 호출함
 */
@Service
class ProductKeywordSearchFacade(
    private val productKeywordSearchService: ProductKeywordSearchService,
    private val productLikeStatusService: ProductLikeStatusService,
    private val eventPublisher: ApplicationEventPublisher,
    transactionManager: PlatformTransactionManager,
) : ProductKeywordSearchUseCase {
    private val readOnlyTx = TransactionTemplate(transactionManager).apply { isReadOnly = true }

    override fun searchProducts(
        query: ProductKeywordSearchQuery,
        searcherId: Long?,
    ): CursorPageResult<ProductSummaryResult> =
        if (productKeywordSearchService.searchesDatabase) {
            readOnlyTx.execute { search(query, searcherId) }!!
        } else {
            search(query, searcherId)
        }

    /** 안쪽 트랜잭션은 바깥 트랜잭션이 있으면 거기에 합류함 */
    private fun search(
        query: ProductKeywordSearchQuery,
        searcherId: Long?,
    ): CursorPageResult<ProductSummaryResult> {
        // 커서 앵커(밀리초)와 정밀도를 맞추려 첫 페이지부터 절삭
        val now = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS)
        val categoryIds = readOnlyTx.execute { productKeywordSearchService.searchableCategoryIds(query.categoryId) }!!
        val page = productKeywordSearchService.searchPage(query, now, categoryIds)
        // 비로그인 검색은 찜 조회도 이벤트도 없어 트랜잭션을 더 열지 않음
        if (searcherId == null) return ProductSummaryResult.fromPage(page, emptySet(), now)
        // 검색 이벤트는 AFTER_COMMIT 리스너가 받으므로 커밋되는 트랜잭션 안에서 발행
        return readOnlyTx.execute {
            val likedIds = productLikeStatusService.likedProductIds(searcherId, page.items.map { it.id })
            val result = ProductSummaryResult.fromPage(page, likedIds, now)
            publishSearchedIfNeeded(query, searcherId)
            result
        }!!
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
