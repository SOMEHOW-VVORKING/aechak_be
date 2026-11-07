package com.aechak.application.sns.port.view

import com.aechak.domain.sns.comment.enums.CommentDeletionReason
import com.aechak.domain.sns.comment.enums.CommentStatus
import com.aechak.domain.user.user.enums.UserStatus
import java.time.LocalDateTime

/** 댓글 행과 작성자 표시 정보. 프로필 행이 없는 작성자(탈퇴)는 표시 정보가 null이고 authorStatus는 users 행의 상태다 */
data class SnsCommentView(
    val id: Long,
    val publicId: String,
    val parentId: Long?,
    val authorId: Long,
    val authorPublicId: String?,
    val authorStatus: UserStatus?,
    val authorNickname: String?,
    val authorProfileImageKey: String?,
    val content: String?,
    val status: CommentStatus,
    val deletionReason: CommentDeletionReason?,
    val editedAt: LocalDateTime?,
    val createdAt: LocalDateTime,
)
