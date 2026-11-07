package com.aechak.infra.persistence.sns.like

import com.aechak.domain.sns.like.CommentLike
import com.aechak.domain.sns.like.repository.CommentLikeRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

interface CommentLikeJpaRepository : JpaRepository<CommentLike, Long> {
    // 게시물 좋아요와 같은 멱등 원자 삽입. 중복 키에서 배타 잠금을 잡아 끄기와 켜기 둘이 겹쳐도 교착하지 않는다
    @Modifying
    @Query(
        value =
            "INSERT INTO sns_comment_likes (comment_id, user_id, created_at, updated_at) " +
                "VALUES (:commentId, :userId, NOW(6), NOW(6)) " +
                "ON DUPLICATE KEY UPDATE user_id = user_id",
        nativeQuery = true,
    )
    fun insertOrKeep(
        @Param("commentId") commentId: Long,
        @Param("userId") userId: Long,
    ): Int

    @Modifying
    @Query("delete from CommentLike l where l.commentId = :commentId and l.userId = :userId")
    fun deleteByPair(
        @Param("commentId") commentId: Long,
        @Param("userId") userId: Long,
    ): Int

    @Query("select l.commentId, count(l) from CommentLike l where l.commentId in :commentIds group by l.commentId")
    fun countGroupedByCommentId(
        @Param("commentIds") commentIds: Collection<Long>,
    ): List<Array<Any>>

    @Query("select l.commentId from CommentLike l where l.userId = :userId and l.commentId in :commentIds")
    fun findCommentIds(
        @Param("userId") userId: Long,
        @Param("commentIds") commentIds: Collection<Long>,
    ): List<Long>

    @Modifying
    @Query("delete from CommentLike l where l.userId = :userId")
    fun deleteAllByUserId(
        @Param("userId") userId: Long,
    ): Int
}

@Repository
class CommentLikeRepositoryAdapter(
    private val jpaRepository: CommentLikeJpaRepository,
) : CommentLikeRepository {
    override fun insertIfAbsent(
        commentId: Long,
        userId: Long,
    ) {
        jpaRepository.insertOrKeep(commentId, userId)
    }

    override fun delete(
        commentId: Long,
        userId: Long,
    ): Int = jpaRepository.deleteByPair(commentId, userId)

    // shortcut: 게시물 좋아요처럼 댓글 좋아요 수를 저장하지 않고 볼 때마다 센다. 댓글 목록 응답 P95가 300ms를 넘거나
    // 좋아요가 1만 개를 넘는 댓글이 생기면 수를 저장한다
    override fun countByCommentIds(commentIds: Collection<Long>): Map<Long, Long> {
        if (commentIds.isEmpty()) return emptyMap()
        val counts = jpaRepository.countGroupedByCommentId(commentIds).associate { (it[0] as Long) to (it[1] as Long) }
        return commentIds.associateWith { counts[it] ?: 0L }
    }

    override fun findLikedCommentIdsAmong(
        userId: Long,
        commentIds: Collection<Long>,
    ): Set<Long> = if (commentIds.isEmpty()) emptySet() else jpaRepository.findCommentIds(userId, commentIds).toSet()

    override fun deleteAllByUser(userId: Long) {
        jpaRepository.deleteAllByUserId(userId)
    }
}
