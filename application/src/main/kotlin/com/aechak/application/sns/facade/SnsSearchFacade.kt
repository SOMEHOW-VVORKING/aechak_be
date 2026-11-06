package com.aechak.application.sns.facade

import com.aechak.application.file.usecase.FileUseCase
import com.aechak.application.sns.port.SnsPostMatch
import com.aechak.application.sns.port.SnsSearchRankingStore
import com.aechak.application.sns.service.FollowService
import com.aechak.application.sns.service.PostCardAssembler
import com.aechak.application.sns.service.SnsFeedService
import com.aechak.application.sns.service.SnsSearchService
import com.aechak.application.sns.service.SnsVisibilityService
import com.aechak.application.sns.support.SnsSearchCursorCodec
import com.aechak.application.sns.usecase.SnsSearchUseCase
import com.aechak.application.sns.usecase.query.SnsHashtagPostsQuery
import com.aechak.application.sns.usecase.query.SnsSearchQuery
import com.aechak.application.sns.usecase.query.SnsSearchSort
import com.aechak.application.sns.usecase.result.SnsFeedItemResult
import com.aechak.application.sns.usecase.result.SnsHashtagPostPageResult
import com.aechak.application.sns.usecase.result.SnsHashtagResult
import com.aechak.application.sns.usecase.result.SnsSearchUserResult
import com.aechak.application.sns.usecase.result.SnsUserItemResult.SnsPetSummaryResult
import com.aechak.application.support.CursorPageResult
import com.aechak.application.user.pet.usecase.PetProfileUseCase
import com.aechak.common.error.BusinessException
import com.aechak.common.error.CommonErrorCode
import com.aechak.domain.sns.hashtag.Hashtag
import com.aechak.domain.sns.search.event.SnsKeywordSearchedEvent
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.dao.DataAccessException
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.util.Locale

/** SNS 검색과 해시태그 게시물 목록 진입점 */
@Service
class SnsSearchFacade(
    private val snsSearchService: SnsSearchService,
    private val snsFeedService: SnsFeedService,
    private val snsVisibilityService: SnsVisibilityService,
    private val postCardAssembler: PostCardAssembler,
    private val rankingStore: SnsSearchRankingStore,
    private val followService: FollowService,
    private val petProfileUseCase: PetProfileUseCase,
    private val fileUseCase: FileUseCase,
    private val eventPublisher: ApplicationEventPublisher,
    transactionManager: PlatformTransactionManager,
) : SnsSearchUseCase {
    private val log = LoggerFactory.getLogger(javaClass)

    // 인기순 순위 저장소(Redis)를 트랜잭션 밖에서 부르고 DB 읽기만 이 트랜잭션으로 감싼다
    private val readTx = TransactionTemplate(transactionManager).apply { isReadOnly = true }

    override fun searchPosts(query: SnsSearchQuery): CursorPageResult<SnsFeedItemResult> {
        if (query.words.isEmpty()) return CursorPageResult(emptyList(), if (query.cursor == null) 0 else null, null, false)
        val match = SnsPostMatch.Words(query.words, query.tagWords)
        // 캡션은 대소문자를 가리지 않아 소문자로 묶고 태그 단어도 함께 넣어 조건이 다른 두 검색이 순위와 커서를 나눠 쓰지 않게 한다
        val scope = WORDS_SCOPE + (query.words.map { it.lowercase(Locale.ROOT) } + query.tagWords).joinToString(SCOPE_SEPARATOR)
        return postPage(match, scope, query.sort, query.cursor, query.size, query.viewerId) { page ->
            if (query.cursor != null) return@postPage page
            // 이 읽기 트랜잭션이 커밋된 뒤 최근 검색어를 비동기로 적재한다. 다음 페이지는 같은 검색이라 적재하지 않는다
            query.viewerId?.let { eventPublisher.publishEvent(SnsKeywordSearchedEvent(it, query.keyword)) }
            page.copy(totalCount = snsSearchService.count(match))
        }
    }

    override fun hashtagPosts(query: SnsHashtagPostsQuery): SnsHashtagPostPageResult {
        val name = Hashtag.clean(query.name)
        val hashtag = readTx.execute { snsSearchService.findHashtag(name) }
        if (hashtag == null) {
            // 없는 태그도 잘못된 커서는 90002다
            query.cursor?.let { validateCursor(it, TAG_SCOPE + name, query.sort) }
            return SnsHashtagPostPageResult(SnsHashtagResult(name, 0), CursorPageResult(emptyList(), null, null, false))
        }
        val page =
            postPage(SnsPostMatch.Tag(hashtag.id), TAG_SCOPE + hashtag.name, query.sort, query.cursor, query.size, query.viewerId) { it }
        return SnsHashtagPostPageResult(SnsHashtagResult.from(hashtag), page)
    }

    @Transactional(readOnly = true)
    override fun searchUsers(query: SnsSearchQuery): CursorPageResult<SnsSearchUserResult> {
        val page = snsSearchService.userPage(query)
        // 나를 차단한 사용자는 기본 프로필만 보여서 펫과 팔로우 여부를 읽지 않는다
        val userIds = page.items.filterNot { it.blockingMe }.map { it.userId }
        val followingIds = query.viewerId?.let { followService.findFollowingAmong(it, userIds) }.orEmpty()
        val pets = petProfileUseCase.getDefaultPetSummaries(userIds).associateBy { it.userId }
        return page.map {
            val isMe = it.userId == query.viewerId
            SnsSearchUserResult(
                publicId = it.publicId,
                nickname = it.nickname,
                profileImageUrl = if (it.blockingMe) null else fileUseCase.resolveMediaUrl(it.profileImageKey),
                petSummary = if (it.blockingMe) null else pets[it.userId]?.let(SnsPetSummaryResult::from),
                followerCount = if (it.blockingMe) null else it.followerCount,
                isMe = isMe,
                following = !isMe && !it.blockingMe && it.userId in followingIds,
                blockingMe = it.blockingMe,
            )
        }
    }

    @Transactional(readOnly = true)
    override fun searchHashtags(query: SnsSearchQuery): CursorPageResult<SnsHashtagResult> =
        snsSearchService.hashtagPage(query).map { SnsHashtagResult(it.name, it.postCount) }

    /** 맞는 원글 한 페이지를 카드로 만든다. finish는 같은 읽기 트랜잭션 안에서 페이지를 마무리한다 */
    private fun postPage(
        match: SnsPostMatch,
        scope: String,
        sort: SnsSearchSort,
        cursor: String?,
        size: Int,
        viewerId: Long?,
        finish: (CursorPageResult<SnsFeedItemResult>) -> CursorPageResult<SnsFeedItemResult>,
    ): CursorPageResult<SnsFeedItemResult> {
        val ranking = if (sort == SnsSearchSort.POPULAR) ranking(match, scope, cursor) else null
        return readTx.execute {
            val mutedIds = snsVisibilityService.findMutedIds(viewerId)
            val hidden = snsVisibilityService.findViewerFilter(viewerId) + mutedIds
            val ids =
                if (ranking == null) {
                    snsSearchService.latestPage(match, scope, cursor, size, hidden)
                } else {
                    val cursorOf =
                        ranking.epochMinute?.let { minute ->
                            { offset: Int -> SnsSearchCursorCodec.encodePopular(scope, minute, offset) }
                        }
                    snsFeedService.popularPage(ranking.postIds, ranking.offset, size, hidden, cursorOf)
                }
            val cards = postCardAssembler.assemble(ids.items, viewerId, mutedIds).map { SnsFeedItemResult(it, null) }
            finish(CursorPageResult(cards, null, ids.nextCursor, ids.hasNext))
        }!!
    }

    /**
     * 첫 페이지는 지금 분의 순위를 읽고 없으면 계산해 남긴다. 다음 페이지는 커서의 분에 남긴 순위를 따르고 만료됐거나 읽지 못하면 90002다.
     * 첫 페이지에서 순위를 남기거나 읽지 못하면 계산한 순위로 커서 없이 한 페이지만 준다(인기 피드와 같다).
     */
    private fun ranking(
        match: SnsPostMatch,
        scope: String,
        cursor: String?,
    ): Ranking {
        val key = SnsSearchCursorCodec.scopeHash(scope)
        if (cursor != null) {
            val decoded = SnsSearchCursorCodec.decodePopular(cursor, scope)
            val stored =
                try {
                    rankingStore.find(key, decoded.epochMinute)
                } catch (e: DataAccessException) {
                    log.error("SNS 검색 순위 저장소 오류로 다음 페이지를 90002로 돌려보낸다 (minute={})", decoded.epochMinute, e)
                    throw BusinessException(CommonErrorCode.INVALID_CURSOR, e)
                }
            if (stored == null || decoded.offset >= stored.size) throw BusinessException(CommonErrorCode.INVALID_CURSOR)
            return Ranking(decoded.epochMinute, stored, decoded.offset)
        }
        val minute = snsFeedService.currentMinute()
        val stored =
            try {
                rankingStore.find(key, minute)
            } catch (e: DataAccessException) {
                return unsaved(minute, e, compute(match))
            }
        if (stored != null) return Ranking(minute, stored, 0)
        val computed = compute(match)
        return try {
            rankingStore.saveIfAbsent(key, minute, computed)
            // 남긴 직후 키가 없으면(메모리 부족으로 지워짐, Redis 재시작) 다음 페이지에 쓸 순위가 없다
            rankingStore.find(key, minute)?.let { Ranking(minute, it, 0) } ?: unsaved(minute, null, computed)
        } catch (e: DataAccessException) {
            unsaved(minute, e, computed)
        }
    }

    private fun validateCursor(
        cursor: String,
        scope: String,
        sort: SnsSearchSort,
    ) {
        if (sort ==
            SnsSearchSort.POPULAR
        ) {
            SnsSearchCursorCodec.decodePopular(cursor, scope)
        } else {
            SnsSearchCursorCodec.decodeLatest(cursor, scope)
        }
    }

    private fun compute(match: SnsPostMatch): List<Long> = readTx.execute { snsSearchService.popularRanking(match) }!!

    private fun unsaved(
        minute: Long,
        e: DataAccessException?,
        computed: List<Long>,
    ): Ranking {
        log.error("SNS 검색 순위 저장소 오류로 첫 페이지만 커서 없이 준다 (minute={})", minute, e)
        return Ranking(null, computed, 0)
    }

    /** 인기순 순위와 그 순위를 남긴 분, 이번에 읽을 자리. 분이 null이면 남기지 못한 순위라 다음 페이지가 없다 */
    private data class Ranking(
        val epochMinute: Long?,
        val postIds: List<Long>,
        val offset: Int,
    )

    companion object {
        // 검색 범위 문자열. 단어 검색과 태그 목록의 커서와 순위 키가 섞이지 않게 앞에 종류를 붙인다
        private const val WORDS_SCOPE = "w\u001F"
        private const val TAG_SCOPE = "t\u001F"
        private const val SCOPE_SEPARATOR = "\u001F"
    }
}
