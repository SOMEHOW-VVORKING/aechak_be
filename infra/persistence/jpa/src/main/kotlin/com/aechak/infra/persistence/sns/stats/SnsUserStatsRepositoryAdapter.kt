package com.aechak.infra.persistence.sns.stats

import com.aechak.domain.sns.stats.SnsUserStats
import com.aechak.domain.sns.stats.repository.SnsUserStatsRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

interface SnsUserStatsJpaRepository : JpaRepository<SnsUserStats, Long> {
    // 행이 없으면 0으로 만들고 있으면 바꾸지 않고 배타 잠금만 잡는다
    @Modifying
    @Query(
        value =
            "INSERT INTO sns_user_stats " +
                "(user_id, follower_count, following_count, post_count, created_at, updated_at) " +
                "VALUES (:userId, 0, 0, 0, NOW(6), NOW(6)) " +
                "ON DUPLICATE KEY UPDATE user_id = user_id",
        nativeQuery = true,
    )
    fun insertOrLock(
        @Param("userId") userId: Long,
    ): Int

    @Modifying
    @Query(
        value =
            "INSERT INTO sns_user_stats " +
                "(user_id, follower_count, following_count, post_count, created_at, updated_at) " +
                "VALUES (:userId, 1, 0, 0, NOW(6), NOW(6)) " +
                "ON DUPLICATE KEY UPDATE follower_count = follower_count + 1, updated_at = NOW(6)",
        nativeQuery = true,
    )
    fun increaseFollowerCount(
        @Param("userId") userId: Long,
    ): Int

    @Modifying
    @Query(
        "update SnsUserStats s set s.followerCount = s.followerCount - 1, s.updatedAt = CURRENT_TIMESTAMP " +
            "where s.userId = :userId and s.followerCount > 0",
    )
    fun decreaseFollowerCount(
        @Param("userId") userId: Long,
    ): Int

    @Modifying
    @Query(
        value =
            "INSERT INTO sns_user_stats " +
                "(user_id, follower_count, following_count, post_count, created_at, updated_at) " +
                "VALUES (:userId, 0, 1, 0, NOW(6), NOW(6)) " +
                "ON DUPLICATE KEY UPDATE following_count = following_count + 1, updated_at = NOW(6)",
        nativeQuery = true,
    )
    fun increaseFollowingCount(
        @Param("userId") userId: Long,
    ): Int

    @Modifying
    @Query(
        "update SnsUserStats s set s.followingCount = s.followingCount - 1, s.updatedAt = CURRENT_TIMESTAMP " +
            "where s.userId = :userId and s.followingCount > 0",
    )
    fun decreaseFollowingCount(
        @Param("userId") userId: Long,
    ): Int
}

// shortcut: 인기 사용자 한 명에게 오는 팔로우, 언팔로우, 차단은 그 사용자의 통계 행 잠금 하나를 차례로 기다린다.
// 잠금 대기가 응답 시간에 드러나면 수 갱신을 비동기 카운터로 옮기고 차단 확인은 findDirectionsForShare 잠금 읽기만으로 하도록 바꾼다.
@Repository
class SnsUserStatsRepositoryAdapter(
    private val jpaRepository: SnsUserStatsJpaRepository,
) : SnsUserStatsRepository {
    override fun findByUserId(userId: Long): SnsUserStats? = jpaRepository.findById(userId).orElse(null)

    // 두 트랜잭션이 같은 두 행을 늘 같은 순서로 잠가 교착하지 않는다
    override fun lockInUserIdOrder(
        userId: Long,
        otherUserId: Long,
    ) {
        listOf(userId, otherUserId).sorted().forEach { jpaRepository.insertOrLock(it) }
    }

    override fun increaseFollowerCount(userId: Long): Int = jpaRepository.increaseFollowerCount(userId)

    override fun decreaseFollowerCount(userId: Long): Int = jpaRepository.decreaseFollowerCount(userId)

    override fun increaseFollowingCount(userId: Long): Int = jpaRepository.increaseFollowingCount(userId)

    override fun decreaseFollowingCount(userId: Long): Int = jpaRepository.decreaseFollowingCount(userId)
}
