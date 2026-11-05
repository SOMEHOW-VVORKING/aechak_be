package com.aechak.application.sns.port.view

import com.aechak.domain.sns.post.enums.PostMediaType
import java.time.LocalDateTime

/** 카드 한 장의 게시물과 작성자 열. 리포스트 수는 통계 행이 없으면 0이다 */
data class SnsPostCardView(
    val postId: Long,
    val publicId: String,
    val authorId: Long,
    val authorPublicId: String,
    val authorNickname: String,
    val authorProfileImageKey: String?,
    val petId: Long?,
    val mediaType: PostMediaType,
    val caption: String?,
    val editedAt: LocalDateTime?,
    val createdAt: LocalDateTime,
    val repostCount: Int,
)
