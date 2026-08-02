package com.aechak.application.search.service

import com.aechak.domain.search.keyword.RecommendedKeyword
import com.aechak.domain.search.keyword.repository.RecommendedKeywordRepository
import com.aechak.domain.search.recent.RecentSearch
import com.aechak.domain.search.recent.repository.RecentSearchRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class SearchKeywordService(
    private val recentSearchRepository: RecentSearchRepository,
    private val recommendedKeywordRepository: RecommendedKeywordRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun getRecentKeywords(userId: Long): List<RecentSearch> = recentSearchRepository.findRecentByUserId(userId, MAX_RECENT_KEYWORDS)

    fun getRecommendedKeywords(): List<RecommendedKeyword> = recommendedKeywordRepository.findActiveOrderBySortOrder()

    fun deleteRecentKeyword(
        userId: Long,
        id: Long,
    ) {
        val deleted = recentSearchRepository.delete(userId, id)
        if (deleted == 0) {
            log.info("recent-search delete no-op: userId={}, id={}", userId, id)
        }
    }

    fun deleteAllRecentKeywords(userId: Long) = recentSearchRepository.deleteAll(userId)

    companion object {
        const val MAX_RECENT_KEYWORDS = 10
    }
}
