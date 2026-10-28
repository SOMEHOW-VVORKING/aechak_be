package com.aechak.infra.persistence.sns.visibility

import com.aechak.application.sns.port.SnsVisibilityQueryPort
import com.aechak.application.sns.port.view.SnsPairStateView
import com.aechak.domain.sns.block.QBlock
import com.aechak.domain.sns.follow.QFollow
import com.aechak.domain.sns.mute.QMute
import com.querydsl.jpa.impl.JPAQueryFactory
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Repository

private val block = QBlock.block
private val mute = QMute.mute
private val follow = QFollow.follow

// shortcut: 목록 쿼리는 이 집합을 NOT IN 파라미터로 받는다.
// 한 사용자의 집합이 1,000명을 넘거나 목록 응답 시간이 나빠지면 sns_blocks, sns_mutes에 대한 NOT EXISTS 서브쿼리로 바꾼다.
// 전환 시점을 알 수 있게 두 집합의 크기를 로그로 남긴다.
@Repository
class SnsVisibilityQueryAdapter(
    private val queryFactory: JPAQueryFactory,
) : SnsVisibilityQueryPort {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun findPairState(
        viewerId: Long,
        targetId: Long,
    ): SnsPairStateView {
        val blockers =
            queryFactory
                .select(block.blockerId)
                .from(block)
                .where(
                    block.blockerId
                        .eq(viewerId)
                        .and(block.blockedId.eq(targetId))
                        .or(block.blockerId.eq(targetId).and(block.blockedId.eq(viewerId))),
                ).fetch()
        val muted =
            queryFactory
                .selectOne()
                .from(mute)
                .where(mute.muterId.eq(viewerId), mute.mutedId.eq(targetId))
                .fetchFirst() != null
        val following =
            queryFactory
                .selectOne()
                .from(follow)
                .where(follow.followerId.eq(viewerId), follow.followeeId.eq(targetId))
                .fetchFirst() != null
        return SnsPairStateView(
            blockedByMe = viewerId in blockers,
            blockedByTarget = targetId in blockers,
            mutedByMe = muted,
            following = following,
        )
    }

    override fun findViewerFilter(viewerId: Long): Set<Long> {
        val blockedByMe =
            queryFactory
                .select(block.blockedId)
                .from(block)
                .where(block.blockerId.eq(viewerId))
                .fetch()
        val blockingMe =
            queryFactory
                .select(block.blockerId)
                .from(block)
                .where(block.blockedId.eq(viewerId))
                .fetch()
        return (blockedByMe + blockingMe).toSet().also {
            log.info("SNS 차단 집합 크기 viewerId={} size={}", viewerId, it.size)
        }
    }

    override fun findMutedIds(viewerId: Long): Set<Long> =
        queryFactory
            .select(mute.mutedId)
            .from(mute)
            .where(mute.muterId.eq(viewerId))
            .fetch()
            .toSet()
            .also { log.info("SNS 뮤트 집합 크기 viewerId={} size={}", viewerId, it.size) }
}
