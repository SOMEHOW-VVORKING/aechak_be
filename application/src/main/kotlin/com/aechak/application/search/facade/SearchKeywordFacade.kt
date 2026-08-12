package com.aechak.application.search.facade

import com.aechak.application.search.service.SearchKeywordService
import com.aechak.application.search.usecase.SearchKeywordUseCase
import com.aechak.application.search.usecase.result.RecentKeywordResult
import com.aechak.application.search.usecase.result.RecommendedKeywordResult
import com.aechak.application.search.usecase.result.SearchKeywordsResult
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class SearchKeywordFacade(
    private val searchKeywordService: SearchKeywordService,
) : SearchKeywordUseCase {
    @Transactional
    override fun recordRecentKeyword(
        userId: Long,
        keyword: String,
    ) = searchKeywordService.recordRecentKeyword(userId, keyword)

    @Transactional(readOnly = true)
    override fun getSearchKeywords(userId: Long): SearchKeywordsResult {
        val recentKeywords = searchKeywordService.getRecentKeywords(userId).map(RecentKeywordResult::from)
        val recommendedKeywords = searchKeywordService.getRecommendedKeywords().map(RecommendedKeywordResult::from)
        return SearchKeywordsResult(recentKeywords, recommendedKeywords)
    }

    @Transactional
    override fun deleteRecentKeyword(
        userId: Long,
        id: Long,
    ) = searchKeywordService.deleteRecentKeyword(userId, id)

    @Transactional
    override fun deleteAllRecentKeywords(userId: Long) = searchKeywordService.deleteAllRecentKeywords(userId)
}
