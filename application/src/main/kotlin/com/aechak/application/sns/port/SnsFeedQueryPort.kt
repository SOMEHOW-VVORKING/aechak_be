package com.aechak.application.sns.port

import com.aechak.application.sns.port.view.SnsFeedEntryView
import com.aechak.application.sns.port.view.SnsFeedRepostView
import com.aechak.application.sns.port.view.SnsPostCardView
import com.aechak.application.sns.port.view.SnsPostHashtagView
import com.aechak.application.sns.port.view.SnsPostMediaView
import com.aechak.application.sns.port.view.SnsSaveView
import com.aechak.application.sns.port.view.SnsUserRepostView
import java.time.LocalDateTime

/** 볼 수 있는 작성자는 프로필 행이 있는 사용자다 */
interface SnsFeedQueryPort {
    /**
     * 팔로잉 피드 한 페이지. 원천은 나와 내가 팔로우하는 사용자 중 볼 수 있고 숨김 밖인 사용자다.
     * 원천의 PUBLISHED 원글과 리포스트를 anchor 이하로 모아 원글마다 가장 늦은 시각으로 묶고 (그 시각, 원글 id) 내림차순으로
     * last 뒤부터 limit개 읽는다. 원글이 PUBLISHED이고 작성자가 볼 수 있으며 숨김 밖인 것만 싣는다.
     */
    fun findFollowingPage(
        viewerId: Long,
        hiddenUserIds: Set<Long>,
        anchor: LocalDateTime,
        lastTime: LocalDateTime?,
        lastPostId: Long?,
        limit: Int,
    ): List<SnsFeedEntryView>

    /** postIds마다 팔로잉 피드 원천이 anchor 이하에 한 리포스트 중 가장 늦은 것 */
    fun findLatestReposts(
        viewerId: Long,
        hiddenUserIds: Set<Long>,
        anchor: LocalDateTime,
        postIds: Collection<Long>,
    ): List<SnsFeedRepostView>

    /**
     * 탐색 피드 한 페이지. from과 anchor 사이의 PUBLISHED 원글을 (created_at, id) 내림차순으로 last 뒤부터 limit개 읽는다.
     * from이 null이면 anchor까지의 글을 기간 없이 읽는다. 숨김 사용자의 글을 빼고 로그인이면 나와 내가 팔로우하는 사용자의 글도 뺀다.
     */
    fun findExplorePage(
        viewerId: Long?,
        hiddenUserIds: Set<Long>,
        from: LocalDateTime?,
        anchor: LocalDateTime,
        lastCreatedAt: LocalDateTime?,
        lastPostId: Long?,
        limit: Int,
    ): List<SnsFeedEntryView>

    /**
     * 인기 순위. from과 to 사이의 PUBLISHED 원글 중 점수가 0보다 큰 글의 id를 점수, created_at, id 내림차순으로 limit개 읽는다.
     * from이 null이면 to까지의 글을 기간 없이 읽는다. 점수는 좋아요 수와 리포스트 수에 각 가중치를 곱해 더하고
     * to 기준 경과 시간이 halfLifeHours 지날 때마다 절반이 된다. 보는 사람의 숨김 집합은 넣지 않는다.
     */
    fun findPopularRanking(
        from: LocalDateTime?,
        to: LocalDateTime,
        likeWeight: Int,
        repostWeight: Int,
        halfLifeHours: Int,
        limit: Int,
    ): List<Long>

    /** 프로필 그리드나 영상 탭 한 페이지. 작성자의 PUBLISHED 원글을 (created_at, id) 내림차순으로 last 뒤부터 limit개 읽는다 */
    fun findUserPostPage(
        authorId: Long,
        tab: SnsPostTab,
        lastCreatedAt: LocalDateTime?,
        lastPostId: Long?,
        limit: Int,
    ): List<SnsFeedEntryView>

    /**
     * 프로필 리포스트 탭 한 페이지. 리포스트한 사람의 리포스트를 행 id 내림차순으로 lastRepostId 뒤부터 limit개 읽는다.
     * 원글이 PUBLISHED이고 원글 작성자가 볼 수 있으며 숨김 밖인 것만 싣는다.
     */
    fun findUserRepostPage(
        reposterId: Long,
        hiddenUserIds: Set<Long>,
        lastRepostId: Long?,
        limit: Int,
    ): List<SnsUserRepostView>

    /**
     * 저장됨 탭 한 페이지. 사용자의 저장을 행 id 내림차순으로 lastSaveId 뒤부터 limit개 읽는다.
     * 게시물이 PUBLISHED이고 작성자가 볼 수 있으며 숨김 밖인 것만 싣는다.
     */
    fun findSavePage(
        userId: Long,
        hiddenUserIds: Set<Long>,
        lastSaveId: Long?,
        limit: Int,
    ): List<SnsSaveView>

    /** postIds 중 지금 볼 수 있는 글. PUBLISHED이고 작성자 프로필이 있으며 작성자가 숨김 사용자가 아니다 */
    fun findVisiblePostIds(
        postIds: Collection<Long>,
        hiddenUserIds: Set<Long>,
    ): Set<Long>

    /** 게시물과 작성자, 리포스트 수. 상태는 보지 않는다 */
    fun findCards(postIds: Collection<Long>): List<SnsPostCardView>

    /** 미디어 행을 sort_order 순으로 */
    fun findMedia(postIds: Collection<Long>): List<SnsPostMediaView>

    /** 해시태그 이름을 작성자가 단 순서로 */
    fun findHashtagNames(postIds: Collection<Long>): List<SnsPostHashtagView>
}
