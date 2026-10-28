package com.aechak.application.user.user.usecase.result

import com.aechak.domain.user.user.enums.UserStatus

/** 다른 BC가 보여 줄 사용자 공개 프로필. 프로필 행이 없으면(탈퇴, 온보딩 전) 프로필 필드가 null이다. */
data class UserPublicProfileResult(
    val userId: Long,
    val publicId: String,
    val status: UserStatus,
    val nickname: String?,
    val profileImageUrl: String?,
    val bio: String?,
)
