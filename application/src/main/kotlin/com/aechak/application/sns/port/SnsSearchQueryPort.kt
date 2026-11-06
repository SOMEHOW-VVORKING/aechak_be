package com.aechak.application.sns.port

import com.aechak.application.sns.port.view.SnsFeedEntryView
import java.time.LocalDateTime

/** SNS 검색 조회. 원글은 PUBLISHED이고 작성자 프로필이 있는 것만 고르며 리포스트는 고르지 않는다 */
interface SnsSearchQueryPort {
    /** 맞는 원글을 (created_at, id) 내림차순으로 last 뒤부터 limit개. 숨김 사용자의 글은 뺀다 */
    fun findLatestPage(
        match: SnsPostMatch,
        hiddenUserIds: Set<Long>,
        lastCreatedAt: LocalDateTime?,
        lastPostId: Long?,
        limit: Int,
    ): List<SnsFeedEntryView>

    /** 맞는 원글의 id를 점수, created_at, id 내림차순으로 limit개. 점수가 0인 글도 넣고 보는 사람의 숨김은 넣지 않는다 */
    fun findPopularRanking(
        match: SnsPostMatch,
        repostWeight: Int,
        limit: Int,
    ): List<Long>

    /** 맞는 원글 수. 보는 사람의 숨김은 넣지 않는다 */
    fun count(match: SnsPostMatch): Long
}
