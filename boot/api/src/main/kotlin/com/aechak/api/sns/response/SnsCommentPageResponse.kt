package com.aechak.api.sns.response

import com.aechak.api.sns.response.PostDetailResponse.PostAuthorResponse
import com.aechak.application.sns.usecase.result.SnsCommentItemResult
import com.aechak.application.sns.usecase.result.SnsCommentItemStatus
import com.aechak.application.support.CursorPageResult
import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

/** 댓글 목록 한 페이지. 헤더의 댓글 수는 상세 commentCount를 쓰므로 totalCount를 싣지 않는다 */
data class SnsCommentPageResponse(
    @field:Schema(description = "최상위 댓글 다음에 그 대댓글을 작성 순서로 이어 붙인 목록. 최상위 댓글은 오래된순이다")
    val items: List<SnsCommentItemResponse>,
    val nextCursor: String?,
    val hasNext: Boolean,
) {
    companion object {
        fun from(page: CursorPageResult<SnsCommentItemResult>): SnsCommentPageResponse =
            SnsCommentPageResponse(page.items.map(SnsCommentItemResponse::from), page.nextCursor, page.hasNext)
    }
}

/** 댓글 항목. 안내 문구 항목은 author와 content가 null이고 likeCount가 0이며 불리언이 모두 false다 */
data class SnsCommentItemResponse(
    val publicId: String,
    @field:Schema(description = "대댓글이면 최상위 댓글의 publicId. 최상위 댓글이면 null")
    val parentPublicId: String?,
    @field:Schema(description = "ACTIVE 보이는 댓글, DELETED 지운 최상위 댓글의 안내 문구, WITHDRAWN 탈퇴한 사용자의 댓글 안내 문구")
    val status: SnsCommentItemStatus,
    val author: PostAuthorResponse?,
    @field:Schema(description = "금지어를 가린 뒤의 내용. 안내 문구 항목이면 null")
    val content: String?,
    val likeCount: Long,
    val liked: Boolean,
    @field:Schema(description = "내용을 고친 적이 있으면 true")
    val edited: Boolean,
    @field:Schema(description = "이 항목과 그 최상위 댓글 항목이 모두 ACTIVE면 true")
    val replyable: Boolean,
    @get:JsonProperty("isMine")
    val isMine: Boolean,
    val createdAt: LocalDateTime,
) {
    companion object {
        fun from(result: SnsCommentItemResult): SnsCommentItemResponse =
            SnsCommentItemResponse(
                publicId = result.publicId,
                parentPublicId = result.parentPublicId,
                status = result.status,
                author = result.author?.let { PostAuthorResponse(it.publicId, it.nickname, it.profileImageUrl) },
                content = result.content,
                likeCount = result.likeCount,
                liked = result.liked,
                edited = result.edited,
                replyable = result.replyable,
                isMine = result.isMine,
                createdAt = result.createdAt,
            )
    }
}
