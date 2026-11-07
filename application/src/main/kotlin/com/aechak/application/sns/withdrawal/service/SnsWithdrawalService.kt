package com.aechak.application.sns.withdrawal.service

import com.aechak.domain.sns.block.repository.BlockRepository
import com.aechak.domain.sns.follow.repository.FollowRepository
import com.aechak.domain.sns.like.repository.CommentLikeRepository
import com.aechak.domain.sns.like.repository.PostLikeRepository
import com.aechak.domain.sns.mute.repository.MuteRepository
import com.aechak.domain.sns.repost.repository.RepostRepository
import com.aechak.domain.sns.save.repository.SaveRepository
import com.aechak.domain.sns.stats.repository.SnsPostStatsRepository
import com.aechak.domain.sns.stats.repository.SnsUserStatsRepository
import org.springframework.stereotype.Service

@Service
class SnsWithdrawalService(
    private val followRepository: FollowRepository,
    private val blockRepository: BlockRepository,
    private val muteRepository: MuteRepository,
    private val repostRepository: RepostRepository,
    private val saveRepository: SaveRepository,
    private val postLikeRepository: PostLikeRepository,
    private val commentLikeRepository: CommentLikeRepository,
    private val snsUserStatsRepository: SnsUserStatsRepository,
    private val snsPostStatsRepository: SnsPostStatsRepository,
) {
    /**
     * 탈퇴하는 사용자의 팔로우, 리포스트, 저장, 좋아요와 그 사용자가 건 차단과 뮤트를 지우고 상대의 수를 내린다.
     * 다른 사용자가 이 사용자에게 건 차단과 뮤트는 남긴다. 이 사용자의 통계 행은 지운다.
     */
    fun removeRelations(userId: Long) {
        // 팔로우와 차단처럼 통계 행을 user_id 오름차순으로 잠근다. 잠근 뒤에는 이 사용자의 팔로우가 바뀌지 않는다.
        // 트랜잭션 스냅샷 뒤에 커밋된 팔로우도 DELETE가 지우므로 지울 행은 잠금 읽기로 다시 읽는다
        snsUserStatsRepository.lockAllInUserIdOrder(followCounterpartsOf(userId) + userId)
        val followeeIds = followRepository.findFolloweeIdsForUpdate(userId)
        val followerIds = followRepository.findFollowerIdsForUpdate(userId)
        followRepository.deleteAllOf(userId)
        snsUserStatsRepository.decreaseFollowerCounts(followeeIds)
        snsUserStatsRepository.decreaseFollowingCounts(followerIds)
        snsUserStatsRepository.deleteByUserId(userId)

        val repostedPostIds = repostRepository.findPostIdsByReposterForUpdate(userId)
        repostRepository.deleteAllByReposter(userId)
        snsPostStatsRepository.decreaseRepostCounts(repostedPostIds)

        saveRepository.deleteAllByUser(userId)
        postLikeRepository.deleteAllByUser(userId)
        commentLikeRepository.deleteAllByUser(userId)
        blockRepository.deleteAllByBlocker(userId)
        muteRepository.deleteAllByMuter(userId)
    }

    // 잠그기 전이라 일반 읽기다. 잠금 읽기로 팔로우 행을 먼저 잡으면 통계 행부터 잠그는 팔로우, 언팔로우와 순서가 엇갈린다
    private fun followCounterpartsOf(userId: Long): List<Long> =
        followRepository.findFolloweeIds(userId) + followRepository.findFollowerIds(userId)
}
