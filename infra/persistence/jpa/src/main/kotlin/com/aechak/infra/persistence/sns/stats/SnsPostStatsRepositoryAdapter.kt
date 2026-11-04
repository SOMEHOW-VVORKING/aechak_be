package com.aechak.infra.persistence.sns.stats

import com.aechak.domain.sns.stats.SnsPostStats
import com.aechak.domain.sns.stats.repository.SnsPostStatsRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

interface SnsPostStatsJpaRepository : JpaRepository<SnsPostStats, Long> {
    @Modifying
    @Query(
        value =
            "INSERT INTO sns_post_stats (post_id, repost_count, created_at, updated_at) " +
                "VALUES (:postId, 1, NOW(6), NOW(6)) " +
                "ON DUPLICATE KEY UPDATE repost_count = repost_count + 1, updated_at = NOW(6)",
        nativeQuery = true,
    )
    fun increaseRepostCount(
        @Param("postId") postId: Long,
    ): Int

    @Modifying
    @Query(
        "update SnsPostStats s set s.repostCount = s.repostCount - 1, s.updatedAt = CURRENT_TIMESTAMP " +
            "where s.postId = :postId and s.repostCount > 0",
    )
    fun decreaseRepostCount(
        @Param("postId") postId: Long,
    ): Int

    @Query("select s.repostCount from SnsPostStats s where s.postId = :postId")
    fun findRepostCount(
        @Param("postId") postId: Long,
    ): Int?
}

@Repository
class SnsPostStatsRepositoryAdapter(
    private val jpaRepository: SnsPostStatsJpaRepository,
) : SnsPostStatsRepository {
    override fun increaseRepostCount(postId: Long): Int = jpaRepository.increaseRepostCount(postId)

    override fun decreaseRepostCount(postId: Long): Int = jpaRepository.decreaseRepostCount(postId)

    override fun findRepostCount(postId: Long): Long = jpaRepository.findRepostCount(postId)?.toLong() ?: 0L
}
