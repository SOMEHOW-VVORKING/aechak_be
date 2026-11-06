package com.aechak.application.sns.port.view

import java.time.LocalDateTime

/** 원글 하나에 팔로잉 피드 원천이 한 가장 늦은 리포스트와 리포스트한 사람 */
data class SnsFeedRepostView(
    val postId: Long,
    val reposterPublicId: String,
    val reposterNickname: String,
    val repostedAt: LocalDateTime,
)
