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
    @Transactional(readOnly = true)
    override fun getSearchKeywords(userId: Long): SearchKeywordsResult {
        val recentKeywords = searchKeywordService.getRecentKeywords(userId).map(RecentKeywordResult::from)
        val recommendedKeywords = searchKeywordService.getRecommendedKeywords().map(RecommendedKeywordResult::from)
        return SearchKeywordsResult(recentKeywords, recommendedKeywords)
    }
}
