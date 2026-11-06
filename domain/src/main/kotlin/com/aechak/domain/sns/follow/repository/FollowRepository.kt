package com.aechak.domain.sns.follow.repository

/** 팔로우 관계 저장소 포트 */
interface FollowRepository {
    /** 팔로우 원자 삽입, 새로 들어갔으면 true */
    fun insertIfAbsent(
        followerId: Long,
        followeeId: Long,
    ): Boolean

    /** follower가 건 팔로우 하드 삭제, 삭제된 행 수 반환 */
    fun delete(
        followerId: Long,
        followeeId: Long,
    ): Int

    /** followeeIds 중 follower가 팔로우하는 사용자 id */
    fun findFolloweeIdsAmong(
        followerId: Long,
        followeeIds: Collection<Long>,
    ): Set<Long>

    /** user가 팔로우하는 사용자 id */
    fun findFolloweeIds(followerId: Long): List<Long>

    /** user를 팔로우하는 사용자 id */
    fun findFollowerIds(followeeId: Long): List<Long>

    /** user가 건 팔로우와 user에게 온 팔로우를 모두 하드 삭제 */
    fun deleteAllOf(userId: Long)
}
