package com.aechak.api.search.response

import com.aechak.application.search.usecase.result.RecentKeywordResult
import java.time.LocalDateTime

data class RecentKeywordResponse(
    val id: Long,
    val keyword: String,
    val searchedAt: LocalDateTime,
) {
    companion object {
        fun from(result: RecentKeywordResult): RecentKeywordResponse =
            RecentKeywordResponse(
                id = result.id,
                keyword = result.keyword,
                searchedAt = result.searchedAt,
            )
    }
}
