package com.aechak.application.sns.usecase.result

import com.aechak.application.sns.port.view.SnsFollowUserView
import com.aechak.application.user.pet.usecase.result.DefaultPetSummaryResult

/** 팔로워, 팔로잉 목록 행. following은 보는 사람이 이 행 사용자를 팔로우하는지다 */
data class SnsUserItemResult(
    val publicId: String,
    val nickname: String,
    val profileImageUrl: String?,
    val petSummary: SnsPetSummaryResult?,
    val isMe: Boolean,
    val following: Boolean,
) {
    /** 대표 펫 품종과 ACTIVE 펫 수 */
    data class SnsPetSummaryResult(
        val breedLabel: String,
        val activePetCount: Int,
    ) {
        companion object {
            fun from(summary: DefaultPetSummaryResult): SnsPetSummaryResult =
                SnsPetSummaryResult(summary.breedLabel, summary.activePetCount)
        }
    }

    companion object {
        fun from(
            view: SnsFollowUserView,
            profileImageUrl: String?,
            petSummary: DefaultPetSummaryResult?,
            isMe: Boolean,
            following: Boolean,
        ): SnsUserItemResult =
            SnsUserItemResult(
                publicId = view.publicId,
                nickname = view.nickname,
                profileImageUrl = profileImageUrl,
                petSummary = petSummary?.let(SnsPetSummaryResult::from),
                isMe = isMe,
                following = following,
            )
    }
}
