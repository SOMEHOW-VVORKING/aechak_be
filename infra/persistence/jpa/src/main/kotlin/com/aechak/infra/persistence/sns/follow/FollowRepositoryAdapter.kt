package com.aechak.infra.persistence.sns.follow

import com.aechak.domain.sns.follow.Follow
import com.aechak.domain.sns.follow.repository.FollowRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

interface FollowJpaRepository : JpaRepository<Follow, Long> {
    // 멱등 원자 삽입 (충돌은 0행으로 흡수), 감사 컬럼은 NOT NULL이라 함께 세팅
    @Modifying
    @Query(
        value =
            "INSERT IGNORE INTO sns_follows (follower_id, followee_id, created_at, updated_at) " +
                "VALUES (:followerId, :followeeId, NOW(6), NOW(6))",
        nativeQuery = true,
    )
    fun insertIgnore(
        @Param("followerId") followerId: Long,
        @Param("followeeId") followeeId: Long,
    ): Int

    @Modifying
    @Query("delete from Follow f where f.followerId = :followerId and f.followeeId = :followeeId")
    fun deleteByPair(
        @Param("followerId") followerId: Long,
        @Param("followeeId") followeeId: Long,
    ): Int
}

@Repository
class FollowRepositoryAdapter(
    private val jpaRepository: FollowJpaRepository,
) : FollowRepository {
    override fun insertIfAbsent(
        followerId: Long,
        followeeId: Long,
    ): Boolean = jpaRepository.insertIgnore(followerId, followeeId) > 0

    override fun delete(
        followerId: Long,
        followeeId: Long,
    ): Int = jpaRepository.deleteByPair(followerId, followeeId)
}
