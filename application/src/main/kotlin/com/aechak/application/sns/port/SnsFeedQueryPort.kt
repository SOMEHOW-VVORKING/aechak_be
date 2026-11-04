package com.aechak.application.sns.port

import com.aechak.application.sns.port.view.SnsFeedEntryView
import com.aechak.application.sns.port.view.SnsPostCardView
import com.aechak.application.sns.port.view.SnsPostHashtagView
import com.aechak.application.sns.port.view.SnsPostMediaView
import java.time.LocalDateTime

/** 피드와 카드 읽기 전용 포트. 볼 수 있는 작성자는 프로필 행이 있는 사용자다 */
interface SnsFeedQueryPort {
    /**
     * 탐색 피드 한 페이지. from과 anchor 사이의 PUBLISHED 원글을 (created_at, id) 내림차순으로 last 뒤부터 limit개 읽는다.
     * 숨김 사용자의 글을 빼고 로그인이면 나와 내가 팔로우하는 사용자의 글도 뺀다.
     */
    fun findExplorePage(
        viewerId: Long?,
        hiddenUserIds: Set<Long>,
        from: LocalDateTime,
        anchor: LocalDateTime,
        lastCreatedAt: LocalDateTime?,
        lastPostId: Long?,
        limit: Int,
    ): List<SnsFeedEntryView>

    /** 게시물과 작성자, 리포스트 수. 상태는 보지 않는다 */
    fun findCards(postIds: Collection<Long>): List<SnsPostCardView>

    /** 미디어 행을 sort_order 순으로 */
    fun findMedia(postIds: Collection<Long>): List<SnsPostMediaView>

    /** 해시태그 이름을 작성자가 단 순서로 */
    fun findHashtagNames(postIds: Collection<Long>): List<SnsPostHashtagView>
}
