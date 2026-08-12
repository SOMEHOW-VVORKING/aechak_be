package com.aechak.domain.user.user

import com.aechak.domain.user.user.enums.UserStatus

/**
 * 작성자 표시용 읽기 모델
 * 프로필이 없으면 nickname/profileImageKey는 null로 반환
 */
data class UserAuthor(
    val id: Long,
    val status: UserStatus,
    val nickname: String?,
    val profileImageKey: String?,
)
