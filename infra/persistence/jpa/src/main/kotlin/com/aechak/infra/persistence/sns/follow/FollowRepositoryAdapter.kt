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

    @Query("select f.followeeId from Follow f where f.followerId = :followerId and f.followeeId in :followeeIds")
    fun findFolloweeIds(
        @Param("followerId") followerId: Long,
        @Param("followeeIds") followeeIds: Collection<Long>,
    ): List<Long>

    @Query("select f.followeeId from Follow f where f.followerId = :followerId")
    fun findAllFolloweeIds(
        @Param("followerId") followerId: Long,
    ): List<Long>

    @Query("select f.followerId from Follow f where f.followeeId = :followeeId")
    fun findAllFollowerIds(
        @Param("followeeId") followeeId: Long,
    ): List<Long>

    @Query(value = "SELECT followee_id FROM sns_follows WHERE follower_id = :followerId FOR UPDATE", nativeQuery = true)
    fun lockFolloweeIds(
        @Param("followerId") followerId: Long,
    ): List<Long>

    @Query(value = "SELECT follower_id FROM sns_follows WHERE followee_id = :followeeId FOR UPDATE", nativeQuery = true)
    fun lockFollowerIds(
        @Param("followeeId") followeeId: Long,
    ): List<Long>

    @Modifying
    @Query("delete from Follow f where f.followerId = :userId")
    fun deleteAllByFollower(
        @Param("userId") userId: Long,
    ): Int

    @Modifying
    @Query("delete from Follow f where f.followeeId = :userId")
    fun deleteAllByFollowee(
        @Param("userId") userId: Long,
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

    override fun findFolloweeIdsAmong(
        followerId: Long,
        followeeIds: Collection<Long>,
    ): Set<Long> = jpaRepository.findFolloweeIds(followerId, followeeIds).toSet()

    override fun findFolloweeIds(followerId: Long): List<Long> = jpaRepository.findAllFolloweeIds(followerId)

    override fun findFollowerIds(followeeId: Long): List<Long> = jpaRepository.findAllFollowerIds(followeeId)

    // 잠금 읽기는 트랜잭션 스냅샷이 아니라 최신 커밋을 읽어 같은 트랜잭션의 DELETE가 지울 행과 같다
    override fun findFolloweeIdsForUpdate(followerId: Long): List<Long> = jpaRepository.lockFolloweeIds(followerId)

    override fun findFollowerIdsForUpdate(followeeId: Long): List<Long> = jpaRepository.lockFollowerIds(followeeId)

    // OR 조건 하나로 지우면 두 인덱스를 함께 못 써 표를 훑으므로 방향마다 지운다
    override fun deleteAllOf(userId: Long) {
        jpaRepository.deleteAllByFollower(userId)
        jpaRepository.deleteAllByFollowee(userId)
    }
}
