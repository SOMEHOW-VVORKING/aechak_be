package com.aechak.application.sns.usecase.result

import com.aechak.application.sns.usecase.result.SnsUserItemResult.SnsPetSummaryResult

/** 계정 검색 행. blockingMe면 기본 프로필이라 사진, 펫, 팔로워 수가 null이고 following은 false다 */
data class SnsSearchUserResult(
    val publicId: String,
    val nickname: String,
    val profileImageUrl: String?,
    val petSummary: SnsPetSummaryResult?,
    val followerCount: Long?,
    val isMe: Boolean,
    val following: Boolean,
    val blockingMe: Boolean,
)
