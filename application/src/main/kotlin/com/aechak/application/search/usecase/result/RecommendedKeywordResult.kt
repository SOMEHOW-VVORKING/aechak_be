package com.aechak.application.search.usecase.result

import com.aechak.domain.search.keyword.RecommendedKeyword

data class RecommendedKeywordResult(
    val keyword: String,
) {
    companion object {
        fun from(recommendedKeyword: RecommendedKeyword): RecommendedKeywordResult =
            RecommendedKeywordResult(keyword = recommendedKeyword.keyword)
    }
}
