package com.aechak.application.sns.service

import com.aechak.application.sns.port.SnsVisibilityQueryPort
import com.aechak.application.sns.port.view.SnsPairStateView
import org.springframework.stereotype.Service

/** 읽기 전용 조회의 가시성 판정. 비로그인과 본인은 관계가 없다 */
@Service
class SnsVisibilityService(
    private val visibilityQueryPort: SnsVisibilityQueryPort,
) {
    fun findPairState(
        viewerId: Long?,
        targetId: Long,
    ): SnsPairStateView =
        if (viewerId == null || viewerId == targetId) {
            SnsPairStateView.NONE
        } else {
            visibilityQueryPort.findPairState(viewerId, targetId)
        }

    fun findViewerFilter(viewerId: Long?): Set<Long> = viewerId?.let(visibilityQueryPort::findViewerFilter) ?: emptySet()

    fun findMutedIds(viewerId: Long?): Set<Long> = viewerId?.let(visibilityQueryPort::findMutedIds) ?: emptySet()
}
