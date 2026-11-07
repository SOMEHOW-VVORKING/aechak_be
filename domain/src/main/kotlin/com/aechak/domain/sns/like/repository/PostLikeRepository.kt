package com.aechak.domain.sns.like.repository

/** 게시물 좋아요 저장소 포트 */
interface PostLikeRepository {
    /** 좋아요 원자 삽입. 이미 좋아요했으면 그대로 둔다 */
    fun insertIfAbsent(
        postId: Long,
        userId: Long,
    )

    /** user가 누른 좋아요 하드 삭제, 삭제된 행 수 반환 */
    fun delete(
        postId: Long,
        userId: Long,
    ): Int

    /** 게시물 id마다 좋아요 수. 좋아요가 없는 게시물은 0이다 */
    fun countByPostIds(postIds: Collection<Long>): Map<Long, Long>

    /** postIds 중 user가 좋아요한 게시물 id */
    fun findLikedPostIdsAmong(
        userId: Long,
        postIds: Collection<Long>,
    ): Set<Long>

    /** user가 누른 게시물 좋아요를 모두 하드 삭제 */
    fun deleteAllByUser(userId: Long)
}
