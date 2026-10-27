package com.aechak.domain.sns.stats.repository

import com.aechak.domain.sns.stats.SnsUserStats

interface SnsUserStatsRepository {
    fun findByUserId(userId: Long): SnsUserStats?

    /** 두 사용자의 통계 행을 user_id 오름차순으로 만들거나 배타 잠금 */
    fun lockInUserIdOrder(
        userId: Long,
        otherUserId: Long,
    )

    /** 팔로워 수 +1, 행이 없으면 만든다 */
    fun increaseFollowerCount(userId: Long): Int

    /** 팔로워 수 -1(follower_count > 0), 갱신 행 수 반환 */
    fun decreaseFollowerCount(userId: Long): Int

    /** 팔로잉 수 +1, 행이 없으면 만든다 */
    fun increaseFollowingCount(userId: Long): Int

    /** 팔로잉 수 -1(following_count > 0), 갱신 행 수 반환 */
    fun decreaseFollowingCount(userId: Long): Int
}
