package com.aechak.api.sns.response

import com.aechak.api.sns.response.SnsFeedPageResponse.SnsFeedItemResponse
import com.aechak.application.sns.usecase.result.SnsFeedItemResult
import com.aechak.application.sns.usecase.result.SnsHashtagPostPageResult
import com.aechak.application.support.CursorPageResult
import io.swagger.v3.oas.annotations.media.Schema

/** 게시물 세그먼트. 카드는 피드 카드와 같고 repostedBy는 늘 null이다 */
data class SnsSearchPostPageResponse(
    val items: List<SnsFeedItemResponse>,
    @field:Schema(description = "첫 페이지의 결과 수. 보는 사람의 차단과 뮤트로 바꾸지 않는다. 다음 페이지는 null")
    val totalCount: Long?,
    val nextCursor: String?,
    val hasNext: Boolean,
) {
    companion object {
        fun from(page: CursorPageResult<SnsFeedItemResult>): SnsSearchPostPageResponse =
            SnsSearchPostPageResponse(page.items.map(::feedItem), page.totalCount, page.nextCursor, page.hasNext)
    }
}

/** 해시태그 게시물 목록. hashtag는 매 페이지에 싣고 없는 태그는 게시글 수 0이다 */
data class SnsHashtagPostPageResponse(
    val hashtag: SnsHashtagHeaderResponse,
    val items: List<SnsFeedItemResponse>,
    val nextCursor: String?,
    val hasNext: Boolean,
) {
    data class SnsHashtagHeaderResponse(
        val name: String,
        val postCount: Int,
    )

    companion object {
        fun from(result: SnsHashtagPostPageResult): SnsHashtagPostPageResponse =
            SnsHashtagPostPageResponse(
                hashtag = SnsHashtagHeaderResponse(result.hashtag.name, result.hashtag.postCount),
                items = result.page.items.map(::feedItem),
                nextCursor = result.page.nextCursor,
                hasNext = result.page.hasNext,
            )
    }
}

private fun feedItem(item: SnsFeedItemResult): SnsFeedItemResponse = SnsFeedItemResponse(PostDetailResponse.from(item.post), null)
