package com.aechak.api.user.user.response

import com.aechak.application.user.user.usecase.result.UserMeResult
import com.aechak.domain.user.user.enums.UserStatus
import com.fasterxml.jackson.annotation.JsonProperty

data class UserMeResponse(
    val status: UserStatus,
    val nickname: String?,
    val profileImageUrl: String?,
    val profileImageKey: String?,
    val bio: String?,
    val email: String?,
    val phoneNumber: String?,
    @get:JsonProperty("isPhoneVerified")
    val isPhoneVerified: Boolean,
) {
    companion object {
        fun from(result: UserMeResult): UserMeResponse =
            UserMeResponse(
                status = result.status,
                nickname = result.nickname,
                profileImageUrl = result.profileImageUrl,
                profileImageKey = result.profileImageKey,
                bio = result.bio,
                email = result.email,
                phoneNumber = result.phoneNumber,
                isPhoneVerified = result.isPhoneVerified,
            )
    }
}
