package com.aechak.api.sns.response

import com.aechak.api.sns.response.SnsProfileResponse.SnsPetResponse
import com.aechak.application.sns.usecase.result.SnsLikerItemResult
import com.aechak.application.sns.usecase.result.SnsLikerListResult
import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema

data class SnsLikerListResponse(
    @field:Schema(description = "전체 좋아요 수. items에서 빠진 차단 관계 사용자도 센다")
    val likeCount: Long,
    @field:Schema(description = "뺄 사용자를 뺀 뒤에도 100명을 넘게 남으면 true")
    val hasMore: Boolean,
    val items: List<SnsLikerItemResponse>,
) {
    data class SnsLikerItemResponse(
        val publicId: String,
        val nickname: String,
        val profileImageUrl: String?,
        @field:Schema(description = "그 사용자의 지금 대표 펫. 펫이 없으면 null")
        val pet: SnsPetResponse?,
        @get:JsonProperty("isMe")
        val isMe: Boolean,
        val following: Boolean,
    ) {
        companion object {
            fun from(result: SnsLikerItemResult): SnsLikerItemResponse =
                SnsLikerItemResponse(
                    publicId = result.publicId,
                    nickname = result.nickname,
                    profileImageUrl = result.profileImageUrl,
                    pet = result.pet?.let { SnsPetResponse(it.breedLabel, it.birthYearMonth) },
                    isMe = result.isMe,
                    following = result.following,
                )
        }
    }

    companion object {
        fun from(result: SnsLikerListResult): SnsLikerListResponse =
            SnsLikerListResponse(result.likeCount, result.hasMore, result.items.map(SnsLikerItemResponse::from))
    }
}
