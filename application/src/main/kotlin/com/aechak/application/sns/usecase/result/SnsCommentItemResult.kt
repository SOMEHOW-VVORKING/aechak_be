package com.aechak.application.sns.usecase.result

import java.time.LocalDateTime

/** 댓글 목록과 단건의 항목. 안내 문구 항목은 작성자와 내용이 null이고 수는 0, 불리언은 false다 */
data class SnsCommentItemResult(
    val publicId: String,
    val parentPublicId: String?,
    val status: SnsCommentItemStatus,
    val author: SnsPostAuthorResult?,
    val content: String?,
    val likeCount: Long,
    val liked: Boolean,
    val edited: Boolean,
    val replyable: Boolean,
    val isMine: Boolean,
    val createdAt: LocalDateTime,
)

/** DELETED는 작성자나 운영자가 지운 댓글, WITHDRAWN은 탈퇴한 사용자의 댓글 안내 문구다 */
enum class SnsCommentItemStatus {
    ACTIVE,
    DELETED,
    WITHDRAWN,
}
