package com.aechak.domain.sns.repost.repository

import java.time.LocalDateTime

interface RepostRepository {
    /** 리포스트 원자 삽입, 새로 들어갔으면 true. 두 시각 열은 at으로 채운다 */
    fun insertIfAbsent(
        reposterId: Long,
        postId: Long,
        at: LocalDateTime,
    ): Boolean

    /** reposter가 한 리포스트 하드 삭제, 삭제된 행 수 반환 */
    fun delete(
        reposterId: Long,
        postId: Long,
    ): Int

    fun findRepostedPostIdsAmong(
        reposterId: Long,
        postIds: Collection<Long>,
    ): Set<Long>
}
