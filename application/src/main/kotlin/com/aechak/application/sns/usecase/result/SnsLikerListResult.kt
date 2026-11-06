package com.aechak.application.sns.usecase.result

import com.aechak.application.user.pet.usecase.result.DefaultPetSummaryResult

/** 좋아요한 사람 최근 100명. likeCount는 목록에서 뺀 사용자도 센 전체 수다 */
data class SnsLikerListResult(
    val likeCount: Long,
    val hasMore: Boolean,
    val items: List<SnsLikerItemResult>,
)

/** pet은 그 사용자의 지금 대표 펫이다 */
data class SnsLikerItemResult(
    val publicId: String,
    val nickname: String,
    val profileImageUrl: String?,
    val pet: DefaultPetSummaryResult?,
    val isMe: Boolean,
    val following: Boolean,
)
