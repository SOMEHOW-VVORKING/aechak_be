package com.aechak.api.sns.response

import com.aechak.application.sns.usecase.result.SnsLikeResult

/** 좋아요 켜기와 끄기를 처리한 뒤의 좋아요 수 */
data class SnsLikeResponse(
    val likeCount: Long,
) {
    companion object {
        fun from(result: SnsLikeResult): SnsLikeResponse = SnsLikeResponse(result.likeCount)
    }
}
