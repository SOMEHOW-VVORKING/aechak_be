package com.aechak.application.sns.usecase.result

import com.aechak.domain.sns.post.enums.PostMediaType

/** captionPreview는 텍스트 게시물만 채운다 */
data class SnsPostGridItemResult(
    val publicId: String,
    val mediaType: PostMediaType,
    val thumbnailUrl: String?,
    val photoCount: Int,
    val captionPreview: String?,
)
