package com.aechak.domain.sns.block.repository

/** 차단 관계 저장소 포트 */
interface BlockRepository {
    /** 차단 원자 삽입, 새로 들어갔으면 true */
    fun insertIfAbsent(
        blockerId: Long,
        blockedId: Long,
    ): Boolean

    /** blocker가 건 차단만 하드 삭제, 삭제된 행 수 반환 */
    fun delete(
        blockerId: Long,
        blockedId: Long,
    ): Int

    /** 두 사람 사이의 두 방향 차단을 잠금 읽기(FOR SHARE)로 찾아 차단을 건 사용자 id를 돌려준다 */
    fun findDirectionsForShare(
        viewerId: Long,
        targetId: Long,
    ): Set<Long>
}
