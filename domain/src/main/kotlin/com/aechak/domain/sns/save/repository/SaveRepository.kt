package com.aechak.domain.sns.save.repository

/** 저장 관계 저장소 포트 */
interface SaveRepository {
    /** 저장 원자 삽입. 이미 저장했으면 그대로 둔다 */
    fun insertIfAbsent(
        userId: Long,
        postId: Long,
    )

    /** user가 한 저장 하드 삭제, 삭제된 행 수 반환 */
    fun delete(
        userId: Long,
        postId: Long,
    ): Int

    /** postIds 중 user가 저장한 게시물 id */
    fun findSavedPostIdsAmong(
        userId: Long,
        postIds: Collection<Long>,
    ): Set<Long>
}
