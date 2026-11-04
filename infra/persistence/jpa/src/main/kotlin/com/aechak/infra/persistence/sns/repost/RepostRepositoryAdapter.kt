package com.aechak.infra.persistence.sns.repost

import com.aechak.domain.sns.repost.Repost
import com.aechak.domain.sns.repost.repository.RepostRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

interface RepostJpaRepository : JpaRepository<Repost, Long> {
    // 멱등 원자 삽입 (충돌은 0행으로 흡수). 팔로잉 피드가 게시 시각과 함께 정렬하므로 DB 시각 대신 앱 시각을 넣는다
    @Modifying
    @Query(
        value =
            "INSERT IGNORE INTO sns_reposts (post_id, reposter_id, created_at, updated_at) " +
                "VALUES (:postId, :reposterId, :at, :at)",
        nativeQuery = true,
    )
    fun insertIgnore(
        @Param("reposterId") reposterId: Long,
        @Param("postId") postId: Long,
        @Param("at") at: LocalDateTime,
    ): Int

    @Modifying
    @Query("delete from Repost r where r.reposterId = :reposterId and r.postId = :postId")
    fun deleteByPair(
        @Param("reposterId") reposterId: Long,
        @Param("postId") postId: Long,
    ): Int
}

@Repository
class RepostRepositoryAdapter(
    private val jpaRepository: RepostJpaRepository,
) : RepostRepository {
    override fun insertIfAbsent(
        reposterId: Long,
        postId: Long,
        at: LocalDateTime,
    ): Boolean = jpaRepository.insertIgnore(reposterId, postId, at) > 0

    override fun delete(
        reposterId: Long,
        postId: Long,
    ): Int = jpaRepository.deleteByPair(reposterId, postId)
}
