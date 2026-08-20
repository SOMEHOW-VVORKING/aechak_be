package com.aechak.application.search.listener

import com.aechak.application.search.usecase.SearchKeywordUseCase
import com.aechak.domain.product.search.event.ProductKeywordSearchedEvent
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * 상품 검색 이벤트를 수신하여 로그인 사용자의 최근 검색어를 적재
 */
@Component
class ProductKeywordSearchEventListener(
    private val searchKeywordUseCase: SearchKeywordUseCase,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Async("recentSearchTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleProductKeywordSearched(event: ProductKeywordSearchedEvent) {
        try {
            searchKeywordUseCase.recordRecentKeyword(event.searcherId, event.keyword)
        } catch (e: Exception) {
            log.warn("최근 검색어 적재 실패: userId={}", event.searcherId, e)
        }
    }
}
