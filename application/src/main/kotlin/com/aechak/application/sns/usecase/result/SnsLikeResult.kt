package com.aechak.application.sns.usecase.result

/** 좋아요 켜기나 끄기를 처리한 뒤의 좋아요 수. 보는 사람과 상관없이 같은 값이다 */
data class SnsLikeResult(
    val likeCount: Long,
)
