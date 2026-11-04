package com.aechak.api.sns.response

import com.aechak.application.sns.usecase.result.SnsHashtagResult
import com.aechak.application.sns.usecase.result.SnsHashtagSearchResult
import com.aechak.application.sns.usecase.result.SnsHashtagSearchStatus
import io.swagger.v3.oas.annotations.media.Schema

data class HashtagSearchResponse(
    @field:Schema(description = "정규화한 검색어. EMPTY면 빈 문자열")
    val query: String,
    @field:Schema(description = "INVALID, TOO_LONG, BANNED면 안내를 보여주고 태그를 추가하지 않는다")
    val status: SnsHashtagSearchStatus,
    @field:Schema(description = "EXISTS일 때 이름이 검색어와 같은 태그. 나머지 상태는 null")
    val exact: HashtagItemResponse?,
    @field:Schema(description = "추천 태그(EMPTY) 또는 부분 일치 태그. 게시글 수가 많은 순, 같으면 이름 순이고 exact와 같은 태그는 빠진다")
    val items: List<HashtagItemResponse>,
) {
    data class HashtagItemResponse(
        val name: String,
        val postCount: Int,
    ) {
        companion object {
            fun from(result: SnsHashtagResult): HashtagItemResponse = HashtagItemResponse(result.name, result.postCount)
        }
    }

    companion object {
        fun from(result: SnsHashtagSearchResult): HashtagSearchResponse =
            HashtagSearchResponse(
                query = result.query,
                status = result.status,
                exact = result.exact?.let(HashtagItemResponse::from),
                items = result.items.map(HashtagItemResponse::from),
            )
    }
}
