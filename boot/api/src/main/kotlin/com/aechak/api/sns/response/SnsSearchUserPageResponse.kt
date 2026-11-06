package com.aechak.api.sns.response

import com.aechak.api.sns.response.SnsUserPageResponse.SnsPetSummaryResponse
import com.aechak.application.sns.usecase.result.SnsHashtagResult
import com.aechak.application.sns.usecase.result.SnsSearchUserResult
import com.aechak.application.support.CursorPageResult
import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema

/** 계정 세그먼트 */
data class SnsSearchUserPageResponse(
    val items: List<SnsSearchUserItemResponse>,
    val nextCursor: String?,
    val hasNext: Boolean,
) {
    data class SnsSearchUserItemResponse(
        val publicId: String,
        val nickname: String,
        @field:Schema(description = "blockingMe면 null(기본 프로필 사진)")
        val profileImageUrl: String?,
        @field:Schema(description = "펫이 없거나 blockingMe면 null")
        val petSummary: SnsPetSummaryResponse?,
        @field:Schema(description = "blockingMe면 null")
        val followerCount: Long?,
        @get:JsonProperty("isMe")
        val isMe: Boolean,
        val following: Boolean,
        @field:Schema(description = "이 사용자가 보는 사람을 차단했으면 true. 팔로우 버튼을 숨긴다")
        val blockingMe: Boolean,
    )

    companion object {
        fun from(page: CursorPageResult<SnsSearchUserResult>): SnsSearchUserPageResponse =
            SnsSearchUserPageResponse(
                items =
                    page.items.map {
                        SnsSearchUserItemResponse(
                            publicId = it.publicId,
                            nickname = it.nickname,
                            profileImageUrl = it.profileImageUrl,
                            petSummary = it.petSummary?.let { pet -> SnsPetSummaryResponse(pet.breedLabel, pet.activePetCount) },
                            followerCount = it.followerCount,
                            isMe = it.isMe,
                            following = it.following,
                            blockingMe = it.blockingMe,
                        )
                    },
                nextCursor = page.nextCursor,
                hasNext = page.hasNext,
            )
    }
}

/** 해시태그 세그먼트 */
data class SnsSearchHashtagPageResponse(
    val items: List<SnsSearchHashtagItemResponse>,
    val nextCursor: String?,
    val hasNext: Boolean,
) {
    data class SnsSearchHashtagItemResponse(
        val name: String,
        val postCount: Int,
    )

    companion object {
        fun from(page: CursorPageResult<SnsHashtagResult>): SnsSearchHashtagPageResponse =
            SnsSearchHashtagPageResponse(
                page.items.map { SnsSearchHashtagItemResponse(it.name, it.postCount) },
                page.nextCursor,
                page.hasNext,
            )
    }
}
