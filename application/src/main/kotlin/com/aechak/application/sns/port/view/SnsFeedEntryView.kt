package com.aechak.application.sns.port.view

import java.time.LocalDateTime

/** 피드 목록 쿼리가 고른 게시물 하나. sortTime과 postId가 keyset이다 */
data class SnsFeedEntryView(
    val postId: Long,
    val publicId: String,
    val sortTime: LocalDateTime,
)
