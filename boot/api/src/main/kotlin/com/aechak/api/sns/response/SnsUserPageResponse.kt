package com.aechak.api.sns.response

import com.aechak.application.sns.usecase.result.SnsUserItemResult
import com.aechak.application.support.CursorPageResult
import com.fasterxml.jackson.annotation.JsonProperty

/** 팔로워, 팔로잉 목록. totalCount는 항상 null이다 */
data class SnsUserPageResponse(
    val items: List<SnsUserItemResponse>,
    val totalCount: Long?,
    val nextCursor: String?,
    val hasNext: Boolean,
) {
    data class SnsUserItemResponse(
        val publicId: String,
        val nickname: String,
        val profileImageUrl: String?,
        val petSummary: SnsPetSummaryResponse?,
        @get:JsonProperty("isMe")
        val isMe: Boolean,
        val following: Boolean,
    ) {
        companion object {
            fun from(result: SnsUserItemResult): SnsUserItemResponse =
                SnsUserItemResponse(
                    publicId = result.publicId,
                    nickname = result.nickname,
                    profileImageUrl = result.profileImageUrl,
                    petSummary = result.petSummary?.let { SnsPetSummaryResponse(it.breedLabel, it.activePetCount) },
                    isMe = result.isMe,
                    following = result.following,
                )
        }
    }

    data class SnsPetSummaryResponse(
        val breedLabel: String,
        val activePetCount: Int,
    )

    companion object {
        fun from(page: CursorPageResult<SnsUserItemResult>): SnsUserPageResponse =
            SnsUserPageResponse(
                items = page.items.map(SnsUserItemResponse::from),
                totalCount = page.totalCount,
                nextCursor = page.nextCursor,
                hasNext = page.hasNext,
            )
    }
}
