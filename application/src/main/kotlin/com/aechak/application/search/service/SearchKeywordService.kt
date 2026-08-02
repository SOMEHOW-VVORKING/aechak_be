package com.aechak.application.search.service

import com.aechak.domain.search.keyword.RecommendedKeyword
import com.aechak.domain.search.keyword.repository.RecommendedKeywordRepository
import com.aechak.domain.search.recent.RecentSearch
import com.aechak.domain.search.recent.repository.RecentSearchRepository
import org.springframework.stereotype.Service

@Service
class SearchKeywordService(
    private val recentSearchRepository: RecentSearchRepository,
    private val recommendedKeywordRepository: RecommendedKeywordRepository,
) {
    fun getRecentKeywords(userId: Long): List<RecentSearch> = recentSearchRepository.findRecentByUserId(userId, MAX_RECENT_KEYWORDS)

    fun getRecommendedKeywords(): List<RecommendedKeyword> = recommendedKeywordRepository.findActiveOrderBySortOrder()

    companion object {
        const val MAX_RECENT_KEYWORDS = 10
    }
}
