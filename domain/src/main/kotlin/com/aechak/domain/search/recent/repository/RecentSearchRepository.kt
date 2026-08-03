package com.aechak.domain.search.recent.repository

import com.aechak.domain.search.recent.RecentSearch

interface RecentSearchRepository {
    /** 사용자의 최근 검색어를 최신순(searched_at desc)으로 최대 [limit]건 조회한다. */
    fun findRecentByUserId(
        userId: Long,
        limit: Int,
    ): List<RecentSearch>

    /** 사용자의 최근 검색어 한 건을 하드 삭제하고, 삭제된 행 수를 돌려준다(0이면 no-op). */
    fun delete(
        userId: Long,
        id: Long,
    ): Int

    /** 사용자의 최근 검색어를 전부 하드 삭제한다. */
    fun deleteAll(userId: Long)
}
