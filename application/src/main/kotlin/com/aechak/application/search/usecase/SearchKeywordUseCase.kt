package com.aechak.application.search.usecase

import com.aechak.application.search.usecase.result.SearchKeywordsResult

interface SearchKeywordUseCase {
    /** 검색 실행 시 로그인 사용자의 최근 검색어로 [keyword]를 적재 */
    fun recordRecentKeyword(
        userId: Long,
        keyword: String,
    )

    fun getSearchKeywords(userId: Long): SearchKeywordsResult

    fun deleteRecentKeyword(
        userId: Long,
        id: Long,
    )

    fun deleteAllRecentKeywords(userId: Long)
}
