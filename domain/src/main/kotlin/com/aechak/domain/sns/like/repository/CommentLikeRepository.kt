package com.aechak.domain.sns.like.repository

/** 댓글 좋아요 저장소 포트 */
interface CommentLikeRepository {
    /** 좋아요 원자 삽입. 이미 좋아요했으면 그대로 둔다 */
    fun insertIfAbsent(
        commentId: Long,
        userId: Long,
    )

    /** user가 누른 좋아요 하드 삭제, 삭제된 행 수 반환 */
    fun delete(
        commentId: Long,
        userId: Long,
    ): Int

    /** 댓글 id마다 좋아요 수. 좋아요가 없는 댓글은 0이다 */
    fun countByCommentIds(commentIds: Collection<Long>): Map<Long, Long>

    /** commentIds 중 user가 좋아요한 댓글 id */
    fun findLikedCommentIdsAmong(
        userId: Long,
        commentIds: Collection<Long>,
    ): Set<Long>
}
