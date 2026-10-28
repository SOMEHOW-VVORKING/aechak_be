package com.aechak.application.sns.usecase.result

import com.aechak.application.user.pet.usecase.result.PetProfileResult

/** SNS 프로필. 펫은 대표 펫부터 ACTIVE 펫 전체다. 내가 차단한 사용자면 소개글, 펫, 세 수가 null이다. */
data class SnsProfileResult(
    val publicId: String,
    val nickname: String,
    val profileImageUrl: String?,
    val bio: String?,
    val pets: List<PetProfileResult>?,
    val postCount: Long?,
    val followerCount: Long?,
    val followingCount: Long?,
    val isMe: Boolean,
    val following: Boolean,
    val blockedByMe: Boolean,
    val mutedByMe: Boolean,
)
