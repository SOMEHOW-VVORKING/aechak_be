package com.aechak.application.sns.service

import com.aechak.common.error.BusinessException
import com.aechak.domain.sns.block.repository.BlockRepository
import com.aechak.domain.sns.error.SnsErrorCode
import com.aechak.domain.sns.repost.repository.RepostRepository
import com.aechak.domain.sns.stats.repository.SnsPostStatsRepository
import com.aechak.domain.sns.stats.repository.SnsUserStatsRepository
import org.springframework.stereotype.Service
import java.time.LocalDateTime

// shortcut: 한 작성자의 글에 리포스트가 몰리면 그 작성자의 통계 행 잠금 하나를 차례로 기다린다.
// 리포스트 시각(at)은 부르는 쪽이 잠금을 기다리기 전에 정해서 오래 기다린 리포스트가 팔로잉 피드를 내리는 중간에 끼어들 수 있다.
// 리포스트 응답 시간에 잠금 대기가 드러나면 쌍 잠금을 MySQL GET_LOCK이나 쌍 잠금 전용 표로 옮기고 시각을 잠금을 얻은 뒤에 정한다.
@Service
class RepostService(
    private val repostRepository: RepostRepository,
    private val blockRepository: BlockRepository,
    private val snsUserStatsRepository: SnsUserStatsRepository,
    private val snsPostStatsRepository: SnsPostStatsRepository,
) {
    /** 차단과 같은 순서로 두 사람의 통계 행을 잠근 뒤 차단을 잠금 읽기로 확인하고 새로 들어갔을 때만 원글 수를 올린다. 뮤트는 보지 않는다 */
    fun repost(
        userId: Long,
        postId: Long,
        authorId: Long,
        at: LocalDateTime,
    ) {
        snsUserStatsRepository.lockInUserIdOrder(userId, authorId)
        val blockerIds = blockRepository.findDirectionsForShare(userId, authorId)
        if (authorId in blockerIds) throw BusinessException(SnsErrorCode.SNS_POST_UNAVAILABLE)
        if (userId in blockerIds) throw BusinessException(SnsErrorCode.SNS_USER_BLOCKED_BY_VIEWER)
        if (repostRepository.insertIfAbsent(userId, postId, at)) snsPostStatsRepository.increaseRepostCount(postId)
    }

    fun unrepost(
        userId: Long,
        postId: Long,
    ) {
        if (repostRepository.delete(userId, postId) > 0) snsPostStatsRepository.decreaseRepostCount(postId)
    }
}
