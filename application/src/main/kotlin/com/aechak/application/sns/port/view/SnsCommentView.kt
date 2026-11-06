package com.aechak.application.sns.port.view

import com.aechak.domain.sns.comment.enums.CommentDeletionReason
import com.aechak.domain.sns.comment.enums.CommentStatus
import java.time.LocalDateTime

/** 댓글 행과 작성자 표시 정보. 프로필 행이 없는 작성자(탈퇴)는 표시 정보가 null이다 */
data class SnsCommentView(
    val id: Long,
    val publicId: String,
    val parentId: Long?,
    val authorId: Long,
    val authorPublicId: String?,
    val authorNickname: String?,
    val authorProfileImageKey: String?,
    val content: String?,
    val status: CommentStatus,
    val deletionReason: CommentDeletionReason?,
    val editedAt: LocalDateTime?,
    val createdAt: LocalDateTime,
)
