package com.aechak.api.search.response

import com.aechak.application.search.usecase.result.SearchKeywordsResult

data class SearchKeywordsResponse(
    val recentKeywords: List<RecentKeywordResponse>,
    val recommendedKeywords: List<RecommendedKeywordResponse>,
) {
    companion object {
        fun from(result: SearchKeywordsResult): SearchKeywordsResponse =
            SearchKeywordsResponse(
                recentKeywords = result.recentKeywords.map(RecentKeywordResponse::from),
                recommendedKeywords = result.recommendedKeywords.map(RecommendedKeywordResponse::from),
            )
    }
}
