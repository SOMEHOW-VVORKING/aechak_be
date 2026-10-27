package com.aechak.application.sns.usecase

import com.aechak.application.sns.usecase.result.SnsProfileResult

interface SnsUseCase {
    /** 다른 사용자의 SNS 프로필. viewerId는 비로그인이면 null이다. */
    fun getProfile(
        publicId: String,
        viewerId: Long?,
    ): SnsProfileResult

    /** 내 SNS 프로필 */
    fun getMyProfile(userId: Long): SnsProfileResult
}
