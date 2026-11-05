package com.aechak.api.sns.response

import com.aechak.application.sns.usecase.result.SnsFeedItemResult
import com.aechak.application.support.CursorPageResult
import io.swagger.v3.oas.annotations.media.Schema

data class SnsFeedPageResponse(
    @field:Schema(description = "같은 원글은 한 페이지에 한 번만 나온다")
    val items: List<SnsFeedItemResponse>,
    val nextCursor: String?,
    val hasNext: Boolean,
) {
    data class SnsFeedItemResponse(
        @field:Schema(description = "게시물 상세와 같은 모양")
        val post: PostDetailResponse,
        @field:Schema(description = "팔로잉 피드에서 카드 순서 시각에 이 원글을 리포스트한 사람. 원글 게시로 들어왔거나 탐색과 인기 피드면 null")
        val repostedBy: SnsFeedReposterResponse?,
    )

    data class SnsFeedReposterResponse(
        val publicId: String,
        val nickname: String,
    )

    companion object {
        fun from(page: CursorPageResult<SnsFeedItemResult>): SnsFeedPageResponse =
            SnsFeedPageResponse(
                items =
                    page.items.map { item ->
                        SnsFeedItemResponse(
                            post = PostDetailResponse.from(item.post),
                            repostedBy = item.repostedBy?.let { SnsFeedReposterResponse(it.publicId, it.nickname) },
                        )
                    },
                nextCursor = page.nextCursor,
                hasNext = page.hasNext,
            )
    }
}
