package com.aechak.application.sns.service

import com.aechak.common.error.BusinessException
import com.aechak.domain.sns.block.repository.BlockRepository
import com.aechak.domain.sns.error.SnsErrorCode
import com.aechak.domain.sns.follow.repository.FollowRepository
import com.aechak.domain.sns.stats.repository.SnsUserStatsRepository
import org.springframework.stereotype.Service

@Service
class BlockService(
    private val blockRepository: BlockRepository,
    private val followRepository: FollowRepository,
    private val snsUserStatsRepository: SnsUserStatsRepository,
) {
    /** 상대가 나를 이미 차단했어도 진행한다. 통계 행은 팔로우와 같은 순서로 잠그고 두 방향 팔로우를 지운다 */
    fun block(
        userId: Long,
        targetUserId: Long,
    ) {
        if (userId == targetUserId) throw BusinessException(SnsErrorCode.CANNOT_BLOCK_SELF)
        snsUserStatsRepository.lockInUserIdOrder(userId, targetUserId)
        blockRepository.insertIfAbsent(userId, targetUserId)
        deleteFollow(userId, targetUserId)
        deleteFollow(targetUserId, userId)
    }

    /** 내가 건 차단만 지우고 팔로우는 되살리지 않는다 */
    fun unblock(
        userId: Long,
        targetUserId: Long,
    ) {
        blockRepository.delete(userId, targetUserId)
    }

    /** 팔로우 행을 지웠을 때만 두 수를 1씩 내린다 */
    private fun deleteFollow(
        followerId: Long,
        followeeId: Long,
    ) {
        if (followRepository.delete(followerId, followeeId) > 0) {
            snsUserStatsRepository.decreaseFollowerCount(followeeId)
            snsUserStatsRepository.decreaseFollowingCount(followerId)
        }
    }
}
