package com.aechak.domain.sns.save.repository

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

    fun findSavedPostIdsAmong(
        userId: Long,
        postIds: Collection<Long>,
    ): Set<Long>
}
