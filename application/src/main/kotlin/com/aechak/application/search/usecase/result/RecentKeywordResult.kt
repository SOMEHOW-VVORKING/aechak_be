package com.aechak.application.search.usecase.result

import com.aechak.domain.search.recent.RecentSearch
import java.time.LocalDateTime

data class RecentKeywordResult(
    val id: Long,
    val keyword: String,
    val searchedAt: LocalDateTime,
) {
    companion object {
        fun from(recentSearch: RecentSearch): RecentKeywordResult =
            RecentKeywordResult(
                id = recentSearch.id,
                keyword = recentSearch.displayKeyword,
                searchedAt = recentSearch.searchedAt,
            )
    }
}
