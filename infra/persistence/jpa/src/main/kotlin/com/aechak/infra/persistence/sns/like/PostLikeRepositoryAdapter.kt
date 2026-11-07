package com.aechak.infra.persistence.sns.like

import com.aechak.domain.sns.like.PostLike
import com.aechak.domain.sns.like.repository.PostLikeRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

interface PostLikeJpaRepository : JpaRepository<PostLike, Long> {
    // 멱등 원자 삽입. 중복 키에서 공유 잠금을 잡는 INSERT IGNORE와 달리 배타 잠금을 잡아 끄기와 켜기 둘이 겹쳐도 교착하지 않는다
    @Modifying
    @Query(
        value =
            "INSERT INTO sns_post_likes (post_id, user_id, created_at, updated_at) " +
                "VALUES (:postId, :userId, NOW(6), NOW(6)) " +
                "ON DUPLICATE KEY UPDATE user_id = user_id",
        nativeQuery = true,
    )
    fun insertOrKeep(
        @Param("postId") postId: Long,
        @Param("userId") userId: Long,
    ): Int

    @Modifying
    @Query("delete from PostLike l where l.postId = :postId and l.userId = :userId")
    fun deleteByPair(
        @Param("postId") postId: Long,
        @Param("userId") userId: Long,
    ): Int

    @Query("select l.postId, count(l) from PostLike l where l.postId in :postIds group by l.postId")
    fun countGroupedByPostId(
        @Param("postIds") postIds: Collection<Long>,
    ): List<Array<Any>>

    @Query("select l.postId from PostLike l where l.userId = :userId and l.postId in :postIds")
    fun findPostIds(
        @Param("userId") userId: Long,
        @Param("postIds") postIds: Collection<Long>,
    ): List<Long>

    @Modifying
    @Query("delete from PostLike l where l.userId = :userId")
    fun deleteAllByUserId(
        @Param("userId") userId: Long,
    ): Int
}

@Repository
class PostLikeRepositoryAdapter(
    private val jpaRepository: PostLikeJpaRepository,
) : PostLikeRepository {
    override fun insertIfAbsent(
        postId: Long,
        userId: Long,
    ) {
        jpaRepository.insertOrKeep(postId, userId)
    }

    override fun delete(
        postId: Long,
        userId: Long,
    ): Int = jpaRepository.deleteByPair(postId, userId)

    // shortcut: 좋아요 수를 저장하지 않고 볼 때마다 게시물의 좋아요 행을 센다. 상세나 좋아요 켜기와 끄기 응답 P95가 200ms를,
    // 피드 응답 P95가 300ms를 넘거나 좋아요가 10만 개를 넘는 게시물이 생기면 수를 게시물 통계 행에 저장한다.
    override fun countByPostIds(postIds: Collection<Long>): Map<Long, Long> {
        val counts = jpaRepository.countGroupedByPostId(postIds).associate { (it[0] as Long) to (it[1] as Long) }
        return postIds.associateWith { counts[it] ?: 0L }
    }

    override fun findLikedPostIdsAmong(
        userId: Long,
        postIds: Collection<Long>,
    ): Set<Long> = jpaRepository.findPostIds(userId, postIds).toSet()

    override fun deleteAllByUser(userId: Long) {
        jpaRepository.deleteAllByUserId(userId)
    }
}
