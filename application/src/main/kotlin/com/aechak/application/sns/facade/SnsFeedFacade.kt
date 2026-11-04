package com.aechak.application.sns.facade

import com.aechak.application.sns.port.SnsPopularSnapshotStore
import com.aechak.application.sns.port.SnsPostTab
import com.aechak.application.sns.service.PostCardAssembler
import com.aechak.application.sns.service.SnsFeedService
import com.aechak.application.sns.service.SnsProfileService
import com.aechak.application.sns.service.SnsVisibilityService
import com.aechak.application.sns.support.SnsFeedCursorCodec
import com.aechak.application.sns.usecase.SnsFeedUseCase
import com.aechak.application.sns.usecase.query.SnsFeedQuery
import com.aechak.application.sns.usecase.query.SnsPostTabQuery
import com.aechak.application.sns.usecase.result.SnsFeedItemResult
import com.aechak.application.sns.usecase.result.SnsFeedReposterResult
import com.aechak.application.sns.usecase.result.SnsPostGridItemResult
import com.aechak.application.support.CursorPageResult
import com.aechak.application.user.user.usecase.UserUseCase
import com.aechak.application.user.user.usecase.result.UserPublicProfileResult
import com.aechak.common.error.BusinessException
import com.aechak.common.error.CommonErrorCode
import org.slf4j.LoggerFactory
import org.springframework.dao.DataAccessException
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate

/** SNS 피드와 프로필 탭 진입점. 금지어 판정과 파일 승격을 쓰지 않아 게시물 쪽 SnsPostFacade와 나눠 둔다 */
@Service
class SnsFeedFacade(
    private val snsFeedService: SnsFeedService,
    private val snsVisibilityService: SnsVisibilityService,
    private val snsProfileService: SnsProfileService,
    private val postCardAssembler: PostCardAssembler,
    private val snapshotStore: SnsPopularSnapshotStore,
    private val userUseCase: UserUseCase,
    transactionManager: PlatformTransactionManager,
) : SnsFeedUseCase {
    private val log = LoggerFactory.getLogger(javaClass)

    // 인기 피드는 순위 저장소(Redis)를 트랜잭션 밖에서 부르고 DB 읽기만 이 트랜잭션으로 감싼다
    private val readTx = TransactionTemplate(transactionManager).apply { isReadOnly = true }

    @Transactional(readOnly = true)
    override fun following(query: SnsFeedQuery): CursorPageResult<SnsFeedItemResult> {
        val viewerId = checkNotNull(query.viewerId) { "팔로잉 피드는 로그인한 사용자만 부른다" }
        val mutedIds = snsVisibilityService.findMutedIds(viewerId)
        val following = snsFeedService.followingPage(query, viewerId, snsVisibilityService.findViewerFilter(viewerId) + mutedIds)
        return cardPage(following.page.items.map { it.postId }, following.page, viewerId, mutedIds, following.reposters)
    }

    @Transactional(readOnly = true)
    override fun explore(query: SnsFeedQuery): CursorPageResult<SnsFeedItemResult> {
        val mutedIds = snsVisibilityService.findMutedIds(query.viewerId)
        val page = snsFeedService.explorePage(query, snsVisibilityService.findViewerFilter(query.viewerId) + mutedIds)
        return cardPage(page.items.map { it.postId }, page, query.viewerId, mutedIds)
    }

    override fun popular(query: SnsFeedQuery): CursorPageResult<SnsFeedItemResult> {
        val cursor = query.cursor?.let(SnsFeedCursorCodec::decodePopular)
        val ranking = if (cursor == null) firstRanking() else nextRanking(cursor)
        return readTx.execute {
            val mutedIds = snsVisibilityService.findMutedIds(query.viewerId)
            val hidden = snsVisibilityService.findViewerFilter(query.viewerId) + mutedIds
            val cursorOf = ranking.epochMinute?.let { minute -> { offset: Int -> SnsFeedCursorCodec.encodePopular(minute, offset) } }
            val page = snsFeedService.popularPage(ranking.postIds, cursor?.offset ?: 0, query.size, hidden, cursorOf)
            cardPage(page.items, page, query.viewerId, mutedIds)
        }!!
    }

    @Transactional(readOnly = true)
    override fun userPosts(query: SnsPostTabQuery): CursorPageResult<SnsPostGridItemResult> = postTabPage(query, SnsPostTab.POSTS)

    @Transactional(readOnly = true)
    override fun userVideos(query: SnsPostTabQuery): CursorPageResult<SnsPostGridItemResult> = postTabPage(query, SnsPostTab.VIDEOS)

    @Transactional(readOnly = true)
    override fun userReposts(query: SnsPostTabQuery): CursorPageResult<SnsPostGridItemResult> {
        val owner = requireVisibleOwner(query)
        // 주인 본인은 자기가 뮤트한 작성자의 원글도 본다
        val mutedIds = if (query.viewerId == owner.userId) emptySet() else snsVisibilityService.findMutedIds(query.viewerId)
        val hidden = snsVisibilityService.findViewerFilter(query.viewerId) + mutedIds
        val page = snsFeedService.userRepostsPage(query, owner.userId, owner.publicId, hidden)
        return cellPage(page.items.map { it.postId }, page)
    }

    @Transactional(readOnly = true)
    override fun mySaves(query: SnsFeedQuery): CursorPageResult<SnsPostGridItemResult> {
        val viewerId = checkNotNull(query.viewerId) { "저장됨 탭은 로그인한 사용자만 부른다" }
        val me = snsProfileService.requireExists(userUseCase.findPublicProfileById(viewerId))
        val hidden = snsVisibilityService.findViewerFilter(viewerId) + snsVisibilityService.findMutedIds(viewerId)
        val page = snsFeedService.mySavesPage(query, viewerId, me.publicId, hidden)
        return cellPage(page.items.map { it.postId }, page)
    }

    private fun postTabPage(
        query: SnsPostTabQuery,
        tab: SnsPostTab,
    ): CursorPageResult<SnsPostGridItemResult> {
        val owner = requireVisibleOwner(query)
        val page = snsFeedService.userPostsPage(query, tab, owner.userId, owner.publicId)
        return cellPage(page.items.map { it.postId }, page)
    }

    /** 페이지의 게시물을 프로필 탭 칸으로 조립한다 */
    private fun cellPage(
        postIds: List<Long>,
        page: CursorPageResult<*>,
    ): CursorPageResult<SnsPostGridItemResult> =
        CursorPageResult(postCardAssembler.assembleCells(postIds), null, page.nextCursor, page.hasNext)

    /** 프로필 탭 주인. 팔로워 목록과 같이 볼 수 없는 사용자와 나를 차단한 주인은 130000, 내가 차단한 주인은 130001이다 */
    private fun requireVisibleOwner(query: SnsPostTabQuery): UserPublicProfileResult {
        val owner = snsProfileService.requireVisible(userUseCase.findPublicProfile(query.ownerPublicId))
        val pair = snsVisibilityService.findPairState(query.viewerId, owner.userId)
        snsProfileService.requireNotBlockedByTarget(pair)
        snsProfileService.requireNotBlockedByViewer(pair)
        return owner
    }

    /** 지금 분의 순위. 없으면 계산해 남기고 같은 분에 먼저 남긴 순위가 있으면 그 순위를 따른다 */
    private fun firstRanking(): PopularRanking {
        val minute = snsFeedService.currentMinute()
        val stored =
            try {
                snapshotStore.find(minute)
            } catch (e: DataAccessException) {
                return unsavedRanking(minute, e, readTx.execute { snsFeedService.popularRanking() }!!)
            }
        if (stored != null) return PopularRanking(minute, stored)
        val computed = readTx.execute { snsFeedService.popularRanking() }!!
        return try {
            snapshotStore.saveIfAbsent(minute, computed)
            // 남긴 직후 키가 없으면(메모리 부족으로 지워짐, Redis 재시작) 다음 페이지에 쓸 순위가 없다
            snapshotStore.find(minute)?.let { PopularRanking(minute, it) } ?: unsavedRanking(minute, null, computed)
        } catch (e: DataAccessException) {
            unsavedRanking(minute, e, computed)
        }
    }

    /** 순위를 남기거나 읽지 못하면 계산한 순위로 첫 페이지만 커서 없이 준다 */
    private fun unsavedRanking(
        minute: Long,
        e: DataAccessException?,
        computed: List<Long>,
    ): PopularRanking {
        log.error("SNS 인기 순위 저장소 오류로 첫 페이지만 커서 없이 준다 (minute={})", minute, e)
        return PopularRanking(epochMinute = null, postIds = computed)
    }

    /** 커서의 분에 남긴 순위. 만료됐거나 읽지 못하면 첫 페이지부터 다시 부르도록 90002다 */
    private fun nextRanking(cursor: SnsFeedCursorCodec.PopularCursor): PopularRanking {
        val stored =
            try {
                snapshotStore.find(cursor.epochMinute)
            } catch (e: DataAccessException) {
                log.error("SNS 인기 순위 저장소 오류로 다음 페이지를 90002로 돌려보낸다 (minute={})", cursor.epochMinute, e)
                throw BusinessException(CommonErrorCode.INVALID_CURSOR, e)
            }
        if (stored == null || cursor.offset >= stored.size) throw BusinessException(CommonErrorCode.INVALID_CURSOR)
        return PopularRanking(cursor.epochMinute, stored)
    }

    /** 페이지의 게시물을 카드로 조립한다. reposters는 게시물 publicId별 리포스트 표시이고 팔로잉 피드만 채운다 */
    private fun cardPage(
        postIds: List<Long>,
        page: CursorPageResult<*>,
        viewerId: Long?,
        mutedIds: Set<Long>,
        reposters: Map<String, SnsFeedReposterResult> = emptyMap(),
    ): CursorPageResult<SnsFeedItemResult> {
        val cards = postCardAssembler.assemble(postIds, viewerId, mutedIds)
        return CursorPageResult(cards.map { SnsFeedItemResult(it, reposters[it.publicId]) }, null, page.nextCursor, page.hasNext)
    }

    /** 인기 순위와 그 순위를 남긴 분. 분이 null이면 남기지 못한 순위라 다음 페이지가 없다 */
    private data class PopularRanking(
        val epochMinute: Long?,
        val postIds: List<Long>,
    )
}
