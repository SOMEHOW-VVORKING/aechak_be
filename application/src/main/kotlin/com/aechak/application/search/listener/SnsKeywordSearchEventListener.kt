package com.aechak.application.search.listener

import com.aechak.application.search.usecase.SearchKeywordUseCase
import com.aechak.domain.sns.search.event.SnsKeywordSearchedEvent
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/** SNS 검색 이벤트를 받아 상품 검색과 같은 최근 검색어 목록에 적재 */
@Component
class SnsKeywordSearchEventListener(
    private val searchKeywordUseCase: SearchKeywordUseCase,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Async("recentSearchTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleSnsKeywordSearched(event: SnsKeywordSearchedEvent) {
        try {
            searchKeywordUseCase.recordRecentKeyword(event.searcherId, event.keyword)
        } catch (e: Exception) {
            log.warn("최근 검색어 적재 실패: userId={}", event.searcherId, e)
        }
    }
}
