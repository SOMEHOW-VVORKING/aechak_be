package com.aechak.application.sns.port

import com.aechak.application.sns.port.view.SnsFeedEntryView
import com.aechak.application.sns.port.view.SnsSearchHashtagView
import com.aechak.application.sns.port.view.SnsSearchUserView
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
        likeWeight: Int,
        commentWeight: Int,
        repostWeight: Int,
        limit: Int,
    ): List<Long>

    /** 맞는 원글 수. 보는 사람의 숨김은 넣지 않는다 */
    fun count(match: SnsPostMatch): Long

    /**
     * 닉네임에 nickname이 든 사용자를 (닉네임 일치 묶음, 팔로워 수 내림차순, publicId) 순서로 last 뒤부터 limit개. 프로필 행이 없는 사용자는 빠진다.
     * viewerId가 있으면 보는 사람이 차단했고 보는 사람을 차단하지 않은 사용자를 빼고 보는 사람을 차단했는지 싣는다
     */
    fun findUserPage(
        nickname: String,
        viewerId: Long?,
        lastBucket: Int?,
        lastFollowerCount: Long?,
        lastPublicId: String?,
        limit: Int,
    ): List<SnsSearchUserView>

    /** 이름에 name이 들고 게시글 수가 1 이상인 태그를 (게시글 수 내림차순, 이름) 순서로 last 뒤부터 limit개 */
    fun findHashtagPage(
        name: String,
        lastPostCount: Int?,
        lastName: String?,
        limit: Int,
    ): List<SnsSearchHashtagView>
}
