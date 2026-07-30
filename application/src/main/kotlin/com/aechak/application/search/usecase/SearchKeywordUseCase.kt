package com.aechak.application.search.usecase

import com.aechak.application.search.usecase.result.SearchKeywordsResult

interface SearchKeywordUseCase {
    fun getSearchKeywords(userId: Long): SearchKeywordsResult
}
