package com.aechak.application.search.usecase.result

data class SearchKeywordsResult(
    val recentKeywords: List<RecentKeywordResult>,
    val recommendedKeywords: List<RecommendedKeywordResult>,
)
