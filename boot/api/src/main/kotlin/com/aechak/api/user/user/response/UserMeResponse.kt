package com.aechak.api.user.user.response

import com.aechak.application.user.user.usecase.result.UserMeResult
import com.aechak.domain.user.user.enums.UserStatus

data class UserMeResponse(
    val status: UserStatus,
    val nickname: String?,
    val profileImageUrl: String?,
    val bio: String?,
    val email: String?,
    val isPhoneVerified: Boolean,
) {
    companion object {
        fun from(result: UserMeResult): UserMeResponse =
            UserMeResponse(
                status = result.status,
                nickname = result.nickname,
                profileImageUrl = result.profileImageUrl,
                bio = result.bio,
                email = result.email,
                isPhoneVerified = result.isPhoneVerified,
            )
    }
}
