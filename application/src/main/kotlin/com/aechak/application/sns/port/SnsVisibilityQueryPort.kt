package com.aechak.application.sns.port

import com.aechak.application.sns.port.view.SnsPairStateView

/** 읽기 전용 가시성 판정. 쓰기 경로의 차단 확인은 이 포트를 쓰지 않는다 */
interface SnsVisibilityQueryPort {
    /** 내가 차단했는지, 나를 차단했는지, 내가 뮤트했는지, 내가 팔로우하는지 */
    fun findPairState(
        viewerId: Long,
        targetId: Long,
    ): SnsPairStateView

    /** 어느 방향으로든 차단 관계인 사용자 id 집합 */
    fun findViewerFilter(viewerId: Long): Set<Long>

    /** 내가 뮤트한 사용자 id 집합 */
    fun findMutedIds(viewerId: Long): Set<Long>
}
