package com.aechak.application.sns.port.view

/** 리포스트 탭이 고른 리포스트 하나. repostId가 keyset이고 postId는 원글이다 */
data class SnsUserRepostView(
    val repostId: Long,
    val postId: Long,
)
