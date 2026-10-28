package com.aechak.application.sns.service

import com.aechak.common.error.BusinessException
import com.aechak.domain.sns.block.repository.BlockRepository
import com.aechak.domain.sns.error.SnsErrorCode
import com.aechak.domain.sns.follow.repository.FollowRepository
import com.aechak.domain.sns.stats.repository.SnsUserStatsRepository
import org.springframework.stereotype.Service

@Service
class FollowService(
    private val followRepository: FollowRepository,
    private val blockRepository: BlockRepository,
    private val snsUserStatsRepository: SnsUserStatsRepository,
) {
    /** 통계 행을 잠근 뒤 차단을 잠금 읽기로 확인하고 새로 들어갔을 때만 두 수를 올린다. 상대가 나를 뮤트했어도 진행한다 */
    fun follow(
        userId: Long,
        targetUserId: Long,
    ) {
        if (userId == targetUserId) throw BusinessException(SnsErrorCode.CANNOT_FOLLOW_SELF)
        snsUserStatsRepository.lockInUserIdOrder(userId, targetUserId)
        val blockerIds = blockRepository.findDirectionsForShare(userId, targetUserId)
        if (targetUserId in blockerIds) throw BusinessException(SnsErrorCode.SNS_USER_UNAVAILABLE)
        if (userId in blockerIds) throw BusinessException(SnsErrorCode.SNS_USER_BLOCKED_BY_VIEWER)
        if (followRepository.insertIfAbsent(userId, targetUserId)) {
            snsUserStatsRepository.increaseFollowerCount(targetUserId)
            snsUserStatsRepository.increaseFollowingCount(userId)
        }
    }

    /** 팔로우와 같은 순서로 통계 행을 잠그고 지웠을 때만 두 수를 내린다 */
    fun unfollow(
        userId: Long,
        targetUserId: Long,
    ) {
        snsUserStatsRepository.lockInUserIdOrder(userId, targetUserId)
        if (followRepository.delete(userId, targetUserId) > 0) {
            snsUserStatsRepository.decreaseFollowerCount(targetUserId)
            snsUserStatsRepository.decreaseFollowingCount(userId)
        }
    }

    /** userIds 중 userId가 팔로우하는 사용자 id */
    fun findFollowingAmong(
        userId: Long,
        userIds: Collection<Long>,
    ): Set<Long> = if (userIds.isEmpty()) emptySet() else followRepository.findFolloweeIdsAmong(userId, userIds)
}
