package com.aechak.application.user.user.port.view

import com.aechak.domain.user.user.enums.UserStatus

/**
 * 작성자 표시용 읽기 모델 — 프로필이 없는 유저도 포함되도록 nickname/profileImageKey는 null 허용.
 * 탈퇴 표기 판정에 status가 필요해 users와 user_profiles를 함께 읽는다.
 */
data class UserAuthorView(
    val id: Long,
    val status: UserStatus,
    val nickname: String?,
    val profileImageKey: String?,
)
