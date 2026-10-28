package com.aechak.api.sns.response

import com.aechak.application.sns.usecase.result.SnsProfileResult
import com.aechak.application.user.pet.usecase.result.PetProfileResult
import com.fasterxml.jackson.annotation.JsonProperty

/** 사용자는 publicId로만 드러내고 펫은 품종과 생년월만 싣는다. */
data class SnsProfileResponse(
    val publicId: String,
    val nickname: String,
    val profileImageUrl: String?,
    val bio: String?,
    val pets: List<SnsPetResponse>?,
    val postCount: Long?,
    val followerCount: Long?,
    val followingCount: Long?,
    @get:JsonProperty("isMe")
    val isMe: Boolean,
    val following: Boolean,
    val blockedByMe: Boolean,
    val mutedByMe: Boolean,
) {
    data class SnsPetResponse(
        val breedLabel: String,
        val birthYearMonth: String?,
    ) {
        companion object {
            fun from(pet: PetProfileResult): SnsPetResponse = SnsPetResponse(pet.breedLabel, pet.birthYearMonth)
        }
    }

    companion object {
        fun from(result: SnsProfileResult): SnsProfileResponse =
            SnsProfileResponse(
                publicId = result.publicId,
                nickname = result.nickname,
                profileImageUrl = result.profileImageUrl,
                bio = result.bio,
                pets = result.pets?.map(SnsPetResponse::from),
                postCount = result.postCount,
                followerCount = result.followerCount,
                followingCount = result.followingCount,
                isMe = result.isMe,
                following = result.following,
                blockedByMe = result.blockedByMe,
                mutedByMe = result.mutedByMe,
            )
    }
}
