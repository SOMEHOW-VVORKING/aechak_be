package com.aechak.application.sns.port.view

/** 팔로우 목록 행. followId는 커서에만, userId는 펫 요약과 팔로우 여부 조회에만 쓴다 */
data class SnsFollowUserView(
    val followId: Long,
    val userId: Long,
    val publicId: String,
    val nickname: String,
    val profileImageKey: String?,
)
