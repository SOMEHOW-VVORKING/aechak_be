package com.aechak.application.sns.port.view

import com.aechak.domain.sns.post.enums.PostMediaKind

/** 카드에 붙일 미디어 행 하나. 영상 행만 썸네일과 길이가 있다 */
data class SnsPostMediaView(
    val postId: Long,
    val mediaKind: PostMediaKind,
    val storageKey: String,
    val thumbnailKey: String?,
    val durationMs: Int?,
)
