package com.aechak.application.sns.port.view

/** 좋아요한 사람 행. userId는 펫 요약과 팔로우 여부 조회에만 쓴다 */
data class SnsLikerView(
    val userId: Long,
    val publicId: String,
    val nickname: String,
    val profileImageKey: String?,
)
