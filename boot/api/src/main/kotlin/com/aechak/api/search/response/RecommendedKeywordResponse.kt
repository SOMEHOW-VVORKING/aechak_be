package com.aechak.api.search.response

import com.aechak.application.search.usecase.result.RecommendedKeywordResult

data class RecommendedKeywordResponse(
    val keyword: String,
) {
    companion object {
        fun from(result: RecommendedKeywordResult): RecommendedKeywordResponse = RecommendedKeywordResponse(keyword = result.keyword)
    }
}
