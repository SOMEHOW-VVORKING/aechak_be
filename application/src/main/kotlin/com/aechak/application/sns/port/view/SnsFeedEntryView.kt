package com.aechak.application.sns.port.view

import java.time.LocalDateTime

/** sortTime과 postId가 keyset이다 */
data class SnsFeedEntryView(
    val postId: Long,
    val publicId: String,
    val sortTime: LocalDateTime,
)
