package com.aechak.application.sns.port.view

/** 계정 검색 행. bucket은 닉네임이 검색어와 같으면 0, 검색어로 시작하면 1, 나머지 2이고 userId는 펫 요약과 팔로우 여부 조회에만 쓴다 */
data class SnsSearchUserView(
    val userId: Long,
    val publicId: String,
    val nickname: String,
    val profileImageKey: String?,
    val followerCount: Long,
    val bucket: Int,
    val blockingMe: Boolean,
)

data class SnsSearchHashtagView(
    val name: String,
    val postCount: Int,
)
