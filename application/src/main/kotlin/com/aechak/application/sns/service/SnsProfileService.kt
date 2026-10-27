package com.aechak.application.sns.service

import com.aechak.application.sns.usecase.result.SnsProfileResult
import com.aechak.application.user.pet.usecase.result.PetProfileResult
import com.aechak.application.user.user.usecase.result.UserPublicProfileResult
import com.aechak.common.error.BusinessException
import com.aechak.domain.sns.error.SnsErrorCode
import com.aechak.domain.sns.stats.repository.SnsUserStatsRepository
import org.springframework.stereotype.Service

@Service
class SnsProfileService(
    private val snsUserStatsRepository: SnsUserStatsRepository,
) {
    /** 없는 사용자와 프로필 행이 없는 사용자(탈퇴, 온보딩 전)는 같은 130000으로 거른다. */
    fun requireVisible(target: UserPublicProfileResult?): UserPublicProfileResult {
        if (target?.nickname == null) throw BusinessException(SnsErrorCode.SNS_USER_UNAVAILABLE)
        return target
    }

    /** 통계 행이 없으면 세 수는 0이다. 관계 필드는 차단, 뮤트, 팔로우가 생기기 전까지 false다. */
    fun toResult(
        target: UserPublicProfileResult,
        viewerId: Long?,
        pets: List<PetProfileResult>,
    ): SnsProfileResult {
        val stats = snsUserStatsRepository.findByUserId(target.userId)
        return SnsProfileResult(
            publicId = target.publicId,
            nickname = checkNotNull(target.nickname),
            profileImageUrl = target.profileImageUrl,
            bio = target.bio?.ifBlank { null },
            pets = pets,
            postCount = stats?.postCount?.toLong() ?: 0L,
            followerCount = stats?.followerCount?.toLong() ?: 0L,
            followingCount = stats?.followingCount?.toLong() ?: 0L,
            isMe = target.userId == viewerId,
            following = false,
            blockedByMe = false,
            mutedByMe = false,
        )
    }
}
