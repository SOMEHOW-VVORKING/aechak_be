package com.aechak.application.sns.service

import com.aechak.application.sns.port.SnsFeedQueryPort
import com.aechak.application.sns.port.SnsPostTab
import com.aechak.application.sns.port.view.SnsFeedEntryView
import com.aechak.application.sns.port.view.SnsSaveView
import com.aechak.application.sns.port.view.SnsUserRepostView
import com.aechak.application.sns.support.PopularScore
import com.aechak.application.sns.support.SnsFeedCursorCodec
import com.aechak.application.sns.usecase.query.SnsFeedQuery
import com.aechak.application.sns.usecase.query.SnsPostTabQuery
import com.aechak.application.sns.usecase.result.SnsFeedReposterResult
import com.aechak.application.support.CursorPageResult
import com.aechak.application.support.CursorPageSize
import com.aechak.common.error.BusinessException
import com.aechak.common.error.CommonErrorCode
import com.aechak.domain.sns.post.Post
import com.aechak.domain.sns.post.repository.PostRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

@Service
class SnsFeedService(
    private val feedQueryPort: SnsFeedQueryPort,
    private val postRepository: PostRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 팔로잉 피드 한 페이지와 카드 순서 시각에 원글을 리포스트한 원천 사용자.
     * 첫 페이지는 지금이 기준 시각이고 다음 페이지도 그 뒤의 게시와 리포스트를 넣지 않는다.
     */
    fun followingPage(
        query: SnsFeedQuery,
        viewerId: Long,
        hiddenUserIds: Set<Long>,
    ): FollowingPage {
        val cursor = query.cursor?.let(SnsFeedCursorCodec::decodeFollowing)
        val anchor = cursor?.anchor ?: LocalDateTime.now().truncatedTo(ChronoUnit.MICROS)
        val lastPostId = cursor?.let { findLastPost(it.lastPostPublicId).id }
        val fetched =
            feedQueryPort.findFollowingPage(
                viewerId,
                hiddenUserIds,
                anchor,
                cursor?.lastTime,
                lastPostId,
                CursorPageSize.fetchLimit(query.size),
            )
        val page =
            CursorPageResult.of(fetched = fetched, size = query.size) {
                SnsFeedCursorCodec.encodeFollowing(anchor, it.sortTime, it.publicId)
            }
        return FollowingPage(page, reposters(viewerId, hiddenUserIds, anchor, page.items))
    }

    /** 가장 늦은 리포스트의 시각이 카드 순서 시각과 같을 때만 리포스트 표시를 싣는다. 다르면 원글 게시로 들어온 카드다 */
    private fun reposters(
        viewerId: Long,
        hiddenUserIds: Set<Long>,
        anchor: LocalDateTime,
        entries: List<SnsFeedEntryView>,
    ): Map<String, SnsFeedReposterResult> {
        if (entries.isEmpty()) return emptyMap()
        val byPostId = entries.associateBy { it.postId }
        return feedQueryPort
            .findLatestReposts(viewerId, hiddenUserIds, anchor, byPostId.keys)
            .mapNotNull { repost ->
                byPostId[repost.postId]
                    ?.takeIf { it.sortTime == repost.repostedAt }
                    ?.let { it.publicId to SnsFeedReposterResult(repost.reposterPublicId, repost.reposterNickname) }
            }.toMap()
    }

    /** 탐색 피드 한 페이지. 첫 페이지는 지금이 기준 시각이고 다음 페이지는 커서의 기준 시각에서 7일을 센다 */
    fun explorePage(
        query: SnsFeedQuery,
        hiddenUserIds: Set<Long>,
    ): CursorPageResult<SnsFeedEntryView> {
        val cursor = query.cursor?.let(SnsFeedCursorCodec::decodeExplore)
        // 커서에 마이크로초까지 담으므로 첫 페이지도 같은 정밀도로 자른다
        val anchor = cursor?.anchor ?: LocalDateTime.now().truncatedTo(ChronoUnit.MICROS)
        val last = cursor?.let { findLastPost(it.lastPostPublicId) }
        val fetched =
            feedQueryPort.findExplorePage(
                query.viewerId,
                hiddenUserIds,
                anchor.minusDays(EXPLORE_DAYS),
                anchor,
                last?.createdAt,
                last?.id,
                CursorPageSize.fetchLimit(query.size),
            )
        return CursorPageResult.of(fetched = fetched, size = query.size) { SnsFeedCursorCodec.encodeExplore(anchor, it.publicId) }
    }

    /** 지금 인기 순위를 정할 분. 순위 키와 인기 커서가 이 값을 쓴다 */
    fun currentMinute(): Long = Instant.now().epochSecond / SECONDS_PER_MINUTE

    /** 인기 순위. 지금부터 72시간 안의 원글을 점수순으로 최대 1,000개 고르고 보는 사람의 숨김 집합은 넣지 않는다 */
    fun popularRanking(): List<Long> {
        val now = LocalDateTime.now()
        // shortcut: 분마다 그 분의 첫 요청이 72시간 원글을 정렬해 순위를 만든다.
        // 72시간 후보가 1만 건을 넘거나 인기 피드 P95가 500ms를 넘으면 순위 계산을 요청 밖의 주기 작업으로 옮기고 점수를 미리 저장한다
        return feedQueryPort
            .findPopularRanking(now.minusHours(POPULAR_HOURS), now, PopularScore.REPOST, PopularScore.MAX_RANK)
            .also { log.info("SNS 인기 순위 크기 size={}", it.size) }
    }

    /**
     * 순위의 offset부터 지금 볼 수 있는 글로 size개를 채운다. 그사이 지운 글, 탈퇴 작성자, 숨김 사용자의 글은 건너뛴다.
     * cursorOf가 null이면 순위를 남기지 못한 첫 페이지라 다음 페이지가 없다.
     */
    fun popularPage(
        ranking: List<Long>,
        offset: Int,
        size: Int,
        hiddenUserIds: Set<Long>,
        cursorOf: ((Int) -> String)?,
    ): CursorPageResult<Long> {
        val rest = ranking.subList(offset, ranking.size)
        val visible = if (rest.isEmpty()) emptySet() else feedQueryPort.findVisiblePostIds(rest, hiddenUserIds)
        val picked = mutableListOf<Long>()
        var next = offset
        while (next < ranking.size && picked.size < size) {
            val postId = ranking[next++]
            if (postId in visible) picked += postId
        }
        val nextCursor = cursorOf?.takeIf { ranking.subList(next, ranking.size).any { it in visible } }?.invoke(next)
        return CursorPageResult(picked, null, nextCursor, nextCursor != null)
    }

    /** 프로필 그리드나 영상 탭 한 페이지. 주인의 PUBLISHED 원글을 최신순으로 고르고 뮤트는 거르지 않는다 */
    fun userPostsPage(
        query: SnsPostTabQuery,
        tab: SnsPostTab,
        ownerId: Long,
        ownerPublicId: String,
    ): CursorPageResult<SnsFeedEntryView> {
        val last = query.cursor?.let { findLastPost(SnsFeedCursorCodec.decodeUserPosts(it, tab, ownerPublicId)) }
        val fetched = feedQueryPort.findUserPostPage(ownerId, tab, last?.createdAt, last?.id, CursorPageSize.fetchLimit(query.size))
        return CursorPageResult.of(fetched = fetched, size = query.size) {
            SnsFeedCursorCodec.encodeUserPosts(tab, ownerPublicId, it.publicId)
        }
    }

    /** 프로필 리포스트 탭 한 페이지. 주인의 리포스트를 최근순으로 고르고 숨김 사용자의 원글은 뺀다 */
    fun userRepostsPage(
        query: SnsPostTabQuery,
        ownerId: Long,
        ownerPublicId: String,
        hiddenUserIds: Set<Long>,
    ): CursorPageResult<SnsUserRepostView> {
        val lastRepostId = query.cursor?.let { SnsFeedCursorCodec.decodeUserReposts(it, ownerPublicId) }
        val fetched = feedQueryPort.findUserRepostPage(ownerId, hiddenUserIds, lastRepostId, CursorPageSize.fetchLimit(query.size))
        return CursorPageResult.of(fetched = fetched, size = query.size) {
            SnsFeedCursorCodec.encodeUserReposts(ownerPublicId, it.repostId)
        }
    }

    /** 저장됨 탭 한 페이지. 내 저장을 최근순으로 고르고 숨김 사용자의 글은 뺀다 */
    fun mySavesPage(
        query: SnsFeedQuery,
        userId: Long,
        userPublicId: String,
        hiddenUserIds: Set<Long>,
    ): CursorPageResult<SnsSaveView> {
        val lastSaveId = query.cursor?.let { SnsFeedCursorCodec.decodeMySaves(it, userPublicId) }
        val fetched = feedQueryPort.findSavePage(userId, hiddenUserIds, lastSaveId, CursorPageSize.fetchLimit(query.size))
        return CursorPageResult.of(fetched = fetched, size = query.size) { SnsFeedCursorCodec.encodeMySaves(userPublicId, it.saveId) }
    }

    /** 커서의 마지막 게시물. 그사이 지운 게시물도 위치로 쓰므로 상태를 보지 않는다 */
    private fun findLastPost(publicId: String): Post =
        postRepository.findByPublicId(publicId) ?: throw BusinessException(CommonErrorCode.INVALID_CURSOR)

    companion object {
        private const val EXPLORE_DAYS = 7L
        private const val POPULAR_HOURS = 72L
        private const val SECONDS_PER_MINUTE = 60L
    }
}

/** 팔로잉 피드 한 페이지의 원글과 원글 publicId별 리포스트 표시 */
data class FollowingPage(
    val page: CursorPageResult<SnsFeedEntryView>,
    val reposters: Map<String, SnsFeedReposterResult>,
)
