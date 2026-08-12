package com.aechak.application.search.service

import com.aechak.domain.search.keyword.RecommendedKeyword
import com.aechak.domain.search.keyword.repository.RecommendedKeywordRepository
import com.aechak.domain.search.recent.RecentSearch
import com.aechak.domain.search.recent.repository.RecentSearchRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class SearchKeywordService(
    private val recentSearchRepository: RecentSearchRepository,
    private val recommendedKeywordRepository: RecommendedKeywordRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** 검색 실행 시 최근 검색어 upsert. 표기 보존 정규화 후 저장, 저장 개수 상한은 미강제(조회에서 컷) */
    fun recordRecentKeyword(
        userId: Long,
        rawKeyword: String,
    ) {
        val keyword = RecentSearch.normalizeKeyword(rawKeyword)
        if (keyword.isBlank()) return
        recentSearchRepository.record(userId, keyword, LocalDateTime.now())
    }

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
