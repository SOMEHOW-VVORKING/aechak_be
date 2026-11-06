package com.aechak.application.sns.usecase.result

import com.aechak.application.user.pet.usecase.result.PetProfileResult
import java.time.LocalDateTime

data class SnsPostDetailResult(
    val publicId: String,
    val author: SnsPostAuthorResult,
    // 게시할 때의 대표 펫. 품종과 생년월은 지금 값이고 그 펫을 지웠으면 null이다
    val pet: PetProfileResult?,
    val caption: String?,
    val hashtags: List<String>,
    val photoUrls: List<String>,
    val video: SnsPostVideoResult?,
    val likeCount: Long,
    val commentCount: Long,
    val repostCount: Long,
    val edited: Boolean,
    val createdAt: LocalDateTime,
    val isMine: Boolean,
    val following: Boolean,
    val mutedByMe: Boolean,
    val reposted: Boolean,
    val saved: Boolean,
    val liked: Boolean,
)

data class SnsPostAuthorResult(
    val publicId: String,
    val nickname: String,
    val profileImageUrl: String?,
)

data class SnsPostVideoResult(
    val url: String,
    val thumbnailUrl: String,
    val durationMs: Int,
)
