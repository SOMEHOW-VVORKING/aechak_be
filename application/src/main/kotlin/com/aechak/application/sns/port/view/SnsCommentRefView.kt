package com.aechak.application.sns.port.view

import com.aechak.domain.sns.comment.enums.CommentStatus

/** 쓰기 경로가 판정에 쓰는 댓글 조회 결과. 엔티티를 읽지 않아 뒤의 잠금 읽기가 최신 행을 받는다 */
data class SnsCommentRefView(
    val id: Long,
    val parentId: Long?,
    val authorId: Long,
    val status: CommentStatus,
)
