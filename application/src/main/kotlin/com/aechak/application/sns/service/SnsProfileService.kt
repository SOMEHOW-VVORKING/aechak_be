package com.aechak.application.sns.service

import com.aechak.application.sns.port.view.SnsPairStateView
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

    /** 끄기 요청 대상. 없는 사용자만 130000이고 탈퇴, 온보딩 전 사용자도 통과한다. */
    fun requireExists(target: UserPublicProfileResult?): UserPublicProfileResult =
        target ?: throw BusinessException(SnsErrorCode.SNS_USER_UNAVAILABLE)

    /** 상대가 나를 차단했으면 서로 차단한 상태도 탈퇴와 같은 130000이다. */
    fun requireNotBlockedByTarget(pair: SnsPairStateView) {
        if (pair.blockedByTarget) throw BusinessException(SnsErrorCode.SNS_USER_UNAVAILABLE)
    }

    /** 내가 차단한 사용자는 publicId, 닉네임, 이미지만 채우고 소개글, 펫, 세 수는 null이다. */
    fun toBlockedResult(
        target: UserPublicProfileResult,
        pair: SnsPairStateView,
    ): SnsProfileResult =
        SnsProfileResult(
            publicId = target.publicId,
            nickname = checkNotNull(target.nickname),
            profileImageUrl = target.profileImageUrl,
            bio = null,
            pets = null,
            postCount = null,
            followerCount = null,
            followingCount = null,
            isMe = false,
            following = pair.following,
            blockedByMe = true,
            mutedByMe = pair.mutedByMe,
        )

    /** 통계 행이 없으면 세 수는 0이다. */
    fun toResult(
        target: UserPublicProfileResult,
        viewerId: Long?,
        pair: SnsPairStateView,
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
            following = pair.following,
            blockedByMe = false,
            mutedByMe = pair.mutedByMe,
        )
    }
}
