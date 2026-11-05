package com.aechak.application.sns.port.view

/** repostId가 keyset이고 postId는 원글이다 */
data class SnsUserRepostView(
    val repostId: Long,
    val postId: Long,
)
