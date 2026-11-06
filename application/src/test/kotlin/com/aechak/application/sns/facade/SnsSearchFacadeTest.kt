package com.aechak.application.sns.facade

import com.aechak.application.sns.port.SnsPostMatch
import com.aechak.application.sns.port.SnsSearchQueryPort
import com.aechak.application.sns.port.SnsSearchRankingStore
import com.aechak.application.sns.port.view.SnsFeedEntryView
import com.aechak.application.sns.port.view.SnsSearchHashtagView
import com.aechak.application.sns.port.view.SnsSearchUserView
import com.aechak.application.sns.service.FollowService
import com.aechak.application.sns.service.PostCardAssembler
import com.aechak.application.sns.service.SnsFeedService
import com.aechak.application.sns.service.SnsSearchService
import com.aechak.application.sns.service.SnsVisibilityService
import com.aechak.application.sns.support.SnsSearchCursorCodec
import com.aechak.application.sns.usecase.query.SnsSearchQuery
import com.aechak.common.error.BusinessException
import com.aechak.common.error.CommonErrorCode
import com.aechak.domain.sns.block.repository.BlockRepository
import com.aechak.domain.sns.hashtag.Hashtag
import com.aechak.domain.sns.hashtag.repository.HashtagRepository
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.dao.QueryTimeoutException
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

/**
 * 계약: 검색 인기순 순위 저장소(Redis)가 실패해도 첫 페이지는 DB에서 계산한 순위로 한 페이지를 다음 커서 없이 주고
 * 다음 페이지는 저장소 오류, 만료된 순위, 순위 길이를 넘는 커서에서 90002인지. 인기 피드와 같은 규칙이다.
 * 통합 테스트의 Redis 컨테이너로는 연결 실패, 시간 초과, 남긴 직후 키가 없어지는 경우를 만들 수 없어 예외를 던지거나 키를 돌려주지 않는 저장소로 본다.
 * 깨지면 Redis 장애가 검색을 500으로 만들거나 남기지 못한 순위의 커서를 내줘 다음 페이지가 다른 순위를 읽는다.
 */
class SnsSearchFacadeTest {
    @Test
    fun `순위를 읽지 못하면 첫 페이지는 계산한 순위로 크기만큼 주고 다음 커서가 없다`() {
        assertUnsavedFirstPage(FakeRankingStore(failOnFind = true))
    }

    @Test
    fun `순위를 남기지 못하면 첫 페이지는 계산한 순위로 크기만큼 주고 다음 커서가 없다`() {
        assertUnsavedFirstPage(FakeRankingStore(failOnSave = true))
    }

    @Test
    fun `남긴 순위를 다시 읽을 때 키가 없으면 첫 페이지는 계산한 순위로 크기만큼 주고 다음 커서가 없다`() {
        assertUnsavedFirstPage(FakeRankingStore(keepSaved = false))
    }

    @Test
    fun `순위를 읽지 못하면 다음 페이지는 90002다`() {
        assertInvalidCursor(FakeRankingStore(failOnFind = true), offset = 1)
    }

    @Test
    fun `커서의 분에 남긴 순위가 없거나 커서 자리가 순위 길이를 넘으면 다음 페이지는 90002다`() {
        assertInvalidCursor(FakeRankingStore(), offset = 1)
        assertInvalidCursor(FakeRankingStore(stored = mapOf(MINUTE to listOf(3L, 2L, 1L))), offset = 3)
    }

    private fun assertUnsavedFirstPage(store: FakeRankingStore) {
        val page = facade(store).searchPosts(SnsSearchQuery(viewerId = null, keyword = "산책", size = 2))

        assertEquals(listOf("post-3", "post-2"), page.items.map { it.post.publicId }, "계산한 순위의 앞에서 크기만큼 줘야 한다")
        assertNull(page.nextCursor, "남기지 못한 순위의 커서를 주면 안 된다")
        assertFalse(page.hasNext, "다음 페이지가 없어야 한다")
        assertEquals(3L, page.totalCount, "첫 페이지에는 결과 수가 있어야 한다")
    }

    private fun assertInvalidCursor(
        store: FakeRankingStore,
        offset: Int,
    ) {
        val cursor = SnsSearchCursorCodec.encodePopular(SCOPE, MINUTE, offset)

        val error =
            assertFailsWith<BusinessException> {
                facade(store).searchPosts(SnsSearchQuery(viewerId = null, keyword = "산책", cursor = cursor, size = 2))
            }

        assertEquals(CommonErrorCode.INVALID_CURSOR, error.errorCode, "다음 페이지는 첫 페이지부터 다시 부르게 90002여야 한다")
    }

    private fun facade(store: SnsSearchRankingStore): SnsSearchFacade {
        val feedPort = FakeFeedQueryPort(ranking = emptyList())
        return SnsSearchFacade(
            SnsSearchService(FakeSearchQueryPort(ranking = listOf(3L, 2L, 1L)), UnusedPostRepository(), UnusedHashtagRepository()),
            SnsFeedService(feedPort, UnusedPostRepository()),
            SnsVisibilityService(UnusedVisibilityQueryPort()),
            PostCardAssembler(
                feedPort,
                UnusedRepostRepository(),
                UnusedSaveRepository(),
                NoPostLikeRepository(),
                UnusedFollowRepository(),
                NoPetProfileUseCase(),
                KeyUrlFileUseCase(),
            ),
            store,
            FollowService(UnusedFollowRepository(), UnusedBlockRepository(), UnusedUserStatsRepository()),
            NoPetProfileUseCase(),
            KeyUrlFileUseCase(),
            { },
            NoOpTransactionManager(),
        )
    }

    private companion object {
        /** keyword 산책의 캡션 단어와 태그 단어로 만든 범위. SnsSearchFacade가 만드는 문자열과 같다 */
        const val SCOPE = "w\u001F산책\u001F산책"
        const val MINUTE = 29_897_280L
    }
}

/**
 * failOnFind면 읽기에서 Redis 시간 초과를, failOnSave면 저장에서 연결 실패를 던진다.
 * keepSaved가 false면 저장은 성공하지만 다시 읽으면 키가 없다(메모리가 모자라 Redis가 키를 지웠거나 재시작한 경우)
 */
private class FakeRankingStore(
    private val failOnFind: Boolean = false,
    private val failOnSave: Boolean = false,
    private val keepSaved: Boolean = true,
    stored: Map<Long, List<Long>> = emptyMap(),
) : SnsSearchRankingStore {
    private val saved = stored.toMutableMap()

    override fun find(
        scope: String,
        epochMinute: Long,
    ): List<Long>? = if (failOnFind) throw QueryTimeoutException("Redis command timed out") else saved[epochMinute]

    override fun saveIfAbsent(
        scope: String,
        epochMinute: Long,
        postIds: List<Long>,
    ) {
        if (failOnSave) throw DataAccessResourceFailureException("Unable to connect to Redis")
        if (keepSaved) saved.putIfAbsent(epochMinute, postIds)
    }
}

/** 인기순 순위는 정해 둔 순위를, 결과 수는 그 순위의 길이를 돌려준다 */
private class FakeSearchQueryPort(
    private val ranking: List<Long>,
) : SnsSearchQueryPort {
    override fun findLatestPage(
        match: SnsPostMatch,
        hiddenUserIds: Set<Long>,
        lastCreatedAt: LocalDateTime?,
        lastPostId: Long?,
        limit: Int,
    ): List<SnsFeedEntryView> = throw UnsupportedOperationException()

    override fun findPopularRanking(
        match: SnsPostMatch,
        likeWeight: Int,
        repostWeight: Int,
        limit: Int,
    ): List<Long> = ranking

    override fun count(match: SnsPostMatch): Long = ranking.size.toLong()

    override fun findUserPage(
        nickname: String,
        viewerId: Long?,
        lastBucket: Int?,
        lastFollowerCount: Long?,
        lastPublicId: String?,
        limit: Int,
    ): List<SnsSearchUserView> = throw UnsupportedOperationException()

    override fun findHashtagPage(
        name: String,
        lastPostCount: Int?,
        lastName: String?,
        limit: Int,
    ): List<SnsSearchHashtagView> = throw UnsupportedOperationException()
}

private class UnusedHashtagRepository : HashtagRepository {
    override fun adjustCounts(
        added: Collection<String>,
        removed: Collection<String>,
    ) = throw UnsupportedOperationException()

    override fun findByName(name: String): Hashtag? = throw UnsupportedOperationException()

    override fun findAllByNameIn(names: Collection<String>): List<Hashtag> = throw UnsupportedOperationException()

    override fun findAllById(ids: Collection<Long>): List<Hashtag> = throw UnsupportedOperationException()

    override fun findPopular(
        keyword: String,
        limit: Int,
    ): List<Hashtag> = throw UnsupportedOperationException()
}

private class UnusedBlockRepository : BlockRepository {
    override fun insertIfAbsent(
        blockerId: Long,
        blockedId: Long,
    ): Boolean = throw UnsupportedOperationException()

    override fun delete(
        blockerId: Long,
        blockedId: Long,
    ): Int = throw UnsupportedOperationException()

    override fun findDirectionsForShare(
        viewerId: Long,
        targetId: Long,
    ): Set<Long> = throw UnsupportedOperationException()
}
