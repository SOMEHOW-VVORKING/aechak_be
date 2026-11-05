package com.aechak.application.sns.port.view

/** 카드에 붙일 해시태그 이름 하나 */
data class SnsPostHashtagView(
    val postId: Long,
    val name: String,
)
