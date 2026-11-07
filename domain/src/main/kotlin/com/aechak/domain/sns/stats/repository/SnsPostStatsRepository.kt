package com.aechak.domain.sns.stats.repository

interface SnsPostStatsRepository {
    /** 리포스트 수 +1, 행이 없으면 만든다 */
    fun increaseRepostCount(postId: Long): Int

    /** 리포스트 수 -1(repost_count > 0), 갱신 행 수 반환 */
    fun decreaseRepostCount(postId: Long): Int

    /** 게시물마다 리포스트 수 -1(repost_count > 0) */
    fun decreaseRepostCounts(postIds: Collection<Long>)
}
