package com.aechak.api.sns

import com.aechak.application.sns.port.SnsPopularSnapshotStore
import org.hibernate.SessionFactory
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 통합: 인기 피드가 72시간 안의 원글을 공개 댓글 수의 2배와 리포스트 수의 3배를 더한 점수순 카드로 주고(20개에 못 미치면 72시간이 지난 글을 점수순으로 잇고)
 * 순위를 분마다 Redis 키 하나에 남겨 다음 페이지가 첫 페이지의 순위를 따르는지, 모든 페이지가 지금의 노출 판정으로 거르며 크기만큼 채우는지.
 * 깨지면 점수 0인 글이 나오거나 72시간 안의 글이 넉넉한데 지난 글이 끼거나 적은데 지난 글로 채우지 않거나
 * 내리는 동안 순위가 바뀌어 본 글이 다시 나오거나 숨긴 사용자의 글이 나온다.
 */
class PopularFeedIntegrationTest : SnsPostIntegrationTestBase() {
    @Autowired
    private lateinit var redisTemplate: StringRedisTemplate

    @Autowired
    private lateinit var snapshotStore: SnsPopularSnapshotStore

    // DB는 테스트마다 비우지만 Redis는 남아서 같은 분에 앞 테스트가 남긴 순위를 지운다
    @BeforeEach
    fun clearRankings() {
        deleteRankings()
    }

    @Test
    fun `리포스트 수로 매긴 순서로 원글을 한 번씩 주고 비로그인도 받는다`() {
        val two = repostedPost(2)
        val three = repostedPost(3)
        val one = repostedPost(1)

        val page = feedPage("popular")

        assertEquals(listOf(three, two, one), page.postIds(), "리포스트가 많은 원글부터 한 번씩 나와야 한다")
        assertTrue(page.feedItems().all { it["repostedBy"] == null }, "인기 피드는 리포스트 표시가 없어야 한다")
        assertEquals(3, (page.feedItems().first()["post"] as Map<*, *>)["repostCount"], "카드의 리포스트 수가 실려야 한다")
    }

    @Test
    fun `첫 페이지가 그 분의 순위를 31분 뒤 만료되는 키 하나에 남긴다`() {
        scoredPost(1)

        feedPage("popular")

        val keys = redisTemplate.keys("sns-popular:*")
        assertEquals(1, keys.size, "순위 키는 하나여야 한다: $keys")
        val ttl = redisTemplate.getExpire(keys.single())
        assertTrue(ttl in 1_800L..1_860L, "키는 31분 뒤 만료되어야 한다: $ttl")
    }

    @Test
    fun `72시간 안의 글이 20개 이상이면 72시간 경계 밖의 글은 나오지 않는다`() {
        val recent = List(19) { scoredPost(1) }
        val inside = scoredPost(1)
        val outside = scoredPost(5)
        backdatePost(inside, LocalDateTime.now().minusHours(72).plusMinutes(10))
        backdatePost(outside, LocalDateTime.now().minusHours(72).minusMinutes(10))

        assertEquals(recent.reversed() + inside, feedPage("popular", size = 30).postIds(), "72시간 안의 글 20개만 나와야 한다")
    }

    @Test
    fun `72시간 안의 글이 20개에 못 미치면 72시간이 지난 글을 그 뒤에 점수순으로 잇는다`() {
        val recent = scoredPost(1)
        val oldLow = scoredPost(2)
        val oldHigh = scoredPost(5)
        backdatePost(oldLow, LocalDateTime.now().minusDays(5))
        backdatePost(oldHigh, LocalDateTime.now().minusDays(10))

        assertEquals(listOf(recent, oldHigh, oldLow), feedPage("popular").postIds(), "72시간 안의 글 뒤에 지난 글이 점수순으로 이어져야 한다")
    }

    @Test
    fun `점수는 공개 댓글 수의 2배와 리포스트 수의 3배를 더한 값이고 댓글만 받은 글과 작성자 본인의 댓글도 넣는다`() {
        val twoComments = createdPublicId(onboardedUser())
        repeat(2) { seedComment(onboardedUser(), twoComments) }
        val oneRepost = scoredPost(1)
        val authorId = onboardedUser()
        val selfCommented = createdPublicId(authorId)
        seedComment(authorId, selfCommented)

        assertEquals(
            listOf(twoComments, oneRepost, selfCommented),
            feedPage("popular").postIds(),
            "댓글 2점, 리포스트 3점의 합으로 정렬하고 본인 댓글만 받은 글도 나와야 한다",
        )
    }

    @Test
    fun `섀도우 처리된 댓글과 그 아래 대댓글, 지운 댓글은 점수에 넣지 않고 차단과 일반 뮤트로는 줄지 않는다`() {
        val viewerId = onboardedUser()
        val shadowAuthor = onboardedUser()
        val shadowed = createdPublicId(shadowAuthor)
        val shadowedId = onboardedUser()
        val shadowedRoot = seedComment(shadowedId, shadowed)
        seedComment(onboardedUser(), shadowed, parentPublicId = shadowedRoot)
        relate(shadowAuthor, shadowedId, "mute")
        val deleted = createdPublicId(onboardedUser())
        deleteCommentRow(seedComment(onboardedUser(), deleted))
        val hiddenFromViewer = createdPublicId(onboardedUser())
        val blockedId = onboardedUser()
        val mutedId = onboardedUser()
        seedComment(blockedId, hiddenFromViewer)
        seedComment(mutedId, hiddenFromViewer)
        relate(viewerId, blockedId, "block")
        relate(viewerId, mutedId, "mute")

        assertEquals(listOf(hiddenFromViewer), feedPage("popular", viewerId).postIds(), "공개 댓글 수가 0인 글은 빠지고 보는 사람의 숨김은 점수를 줄이지 않아야 한다")
    }

    @Test
    fun `반응이 없는 글과 리포스트를 취소해 수가 0으로 남은 글은 나오지 않는다`() {
        val scored = scoredPost(1)
        createdPublicId(onboardedUser())
        val canceled = createdPublicId(onboardedUser())
        val reposterId = onboardedUser()
        repost(canceled, reposterId).andExpect(status().isCreated)
        unrepost(canceled, reposterId).andExpect(status().isNoContent)
        assertEquals(0, repostCountOf(canceled), "취소한 글은 통계 행이 0으로 남아 있어야 한다")

        assertEquals(listOf(scored), feedPage("popular").postIds(), "점수가 0보다 큰 글만 나와야 한다")
    }

    @Test
    fun `보여줄 글이 없으면 빈 목록이다`() {
        val page = feedPage("popular", onboardedUser())

        assertEquals(emptyList(), page.postIds(), "점수가 있는 글이 없으면 빈 목록이어야 한다")
        assertEquals(false, page["hasNext"], "빈 목록은 다음 페이지가 없어야 한다")
        assertNull(page["nextCursor"], "빈 목록은 다음 커서가 없어야 한다")
    }

    @Test
    fun `점수가 같으면 최신 글이 먼저다`() {
        val older = scoredPost(2)
        val newer = scoredPost(2)

        assertEquals(listOf(newer, older), feedPage("popular").postIds(), "동점이면 최신 글이 먼저여야 한다")
    }

    @Test
    fun `팔로우 여부와 내 글을 따지지 않고 카드에 보는 사람 상태를 싣는다`() {
        val viewerId = onboardedUser()
        val followed = onboardedUser()
        relate(viewerId, followed, "follow")
        val mine = scoredPost(2, viewerId)
        val followedPost = scoredPost(1, followed)

        val cards = feedPage("popular", viewerId).feedItems().map { it["post"] as Map<*, *> }

        assertEquals(listOf(mine, followedPost), cards.map { it["publicId"] }, "내 글과 팔로우한 사용자의 글도 나와야 한다")
        assertEquals(true, cards[0]["isMine"], "내 글은 isMine이 true여야 한다")
        assertEquals(true, cards[1]["following"], "팔로우한 사용자의 글은 following이 true여야 한다")
    }

    @Test
    fun `첫 페이지 뒤 리포스트가 늘어도 다음 페이지는 첫 페이지의 순위를 따르고 본 글이 다시 나오지 않는다`() {
        val first = scoredPost(3)
        val second = scoredPost(2)
        val third = scoredPost(1)
        val page1 = feedPage("popular", size = 1)
        assertEquals(listOf(first), page1.postIds(), "첫 페이지는 1위여야 한다")

        repeat(3) { repost(third, onboardedUser()).andExpect(status().isCreated) }
        val page2 = feedPage("popular", cursor = page1["nextCursor"] as String, size = 1)
        val page3 = feedPage("popular", cursor = page2["nextCursor"] as String, size = 1)

        assertEquals(listOf(second), page2.postIds(), "다음 페이지는 첫 페이지의 2위여야 한다")
        assertEquals(listOf(third), page3.postIds(), "점수가 오른 글도 첫 페이지 순위의 자리에 나와야 한다")
        assertEquals(false, page3["hasNext"], "순위 끝에서 멈춰야 한다")
    }

    @Test
    fun `그사이 지운 글과 새로 차단하거나 뮤트한 작성자의 글을 빼고 페이지를 크기만큼 채운다`() {
        val viewerId = onboardedUser()
        val authors = List(7) { onboardedUser() }
        val posts = authors.mapIndexed { index, authorId -> scoredPost(7 - index, authorId) }
        val page1 = feedPage("popular", viewerId, size = 2)
        assertEquals(posts.take(2), page1.postIds(), "첫 페이지는 1위와 2위여야 한다")

        deletePost(posts[2], authors[2]).andExpect(status().isNoContent)
        relate(viewerId, authors[3], "block")
        relate(viewerId, authors[4], "mute")
        val page2 = feedPage("popular", viewerId, cursor = page1["nextCursor"] as String, size = 2)

        assertEquals(listOf(posts[5], posts[6]), page2.postIds(), "숨길 글을 건너뛰고 크기만큼 채워야 한다")
        assertEquals(false, page2["hasNext"], "남은 글이 없으면 멈춰야 한다")
    }

    @Test
    fun `차단을 풀면 그 작성자의 글이 다시 나온다`() {
        val viewerId = onboardedUser()
        val authorId = onboardedUser()
        val publicId = scoredPost(1, authorId)
        relate(viewerId, authorId, "block")
        assertEquals(emptyList(), feedPage("popular", viewerId).postIds(), "차단한 작성자의 글은 빠져야 한다")

        unrelate(viewerId, authorId, "block")

        assertEquals(listOf(publicId), feedPage("popular", viewerId).postIds(), "차단을 풀면 다시 나와야 한다")
    }

    @Test
    fun `지금 분과 다음 분의 키에 넣어 둔 순위가 있으면 계산하지 않고 그 순서를 따른다`() {
        val older = createdPublicId(onboardedUser())
        val newer = createdPublicId(onboardedUser())
        // 둘 다 점수가 0이라 계산한 순위라면 빈 목록이다
        val ranking = listOf(older, newer).joinToString(",") { (postRow(it)["id"] as Number).toString() }
        val minute = Instant.now().epochSecond / 60
        listOf(minute, minute + 1).forEach { redisTemplate.opsForValue().set("sns-popular:$it", ranking, Duration.ofMinutes(5)) }

        assertEquals(listOf(older, newer), feedPage("popular").postIds(), "같은 분에 남은 순위를 그대로 따라야 한다")
    }

    @Test
    fun `순위 키가 사라지면 다음 페이지는 90002다`() {
        scoredPost(2)
        scoredPost(1)
        val page1 = feedPage("popular", size = 1)

        deleteRankings()

        getFeed("popular", cursor = page1["nextCursor"] as String, size = 1)
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(90002))
    }

    @Test
    fun `같은 분에 순위를 다시 남겨도 먼저 남긴 순위가 그대로다`() {
        val minute = Instant.now().epochSecond / 60

        snapshotStore.saveIfAbsent(minute, listOf(1L, 2L))
        snapshotStore.saveIfAbsent(minute, listOf(3L))

        assertEquals(listOf(1L, 2L), snapshotStore.find(minute), "늦게 계산한 요청이 먼저 남긴 순위를 덮으면 안 된다")
    }

    @Test
    fun `20개 페이지와 5개 페이지의 쿼리 수가 같다`() {
        val viewerId = onboardedUser()
        val authors = List(5) { onboardedUser().also { defaultPet(it) } }
        repeat(25) {
            val authorId = authors[it % authors.size]
            scoredPost(1, authorId, postBody(photoKeys = listOf(tmpPhotoKey(authorId)), hashtags = listOf("산책")))
        }
        val statistics = em.entityManagerFactory.unwrap(SessionFactory::class.java).statistics
        statistics.isStatisticsEnabled = true

        // 두 요청 모두 순위를 계산하게 해 분이 바뀌어도 순위 쿼리 수가 같다
        deleteRankings()
        statistics.clear()
        assertEquals(20, feedPage("popular", viewerId, size = 20).postIds().size, "20개 페이지여야 한다")
        val twenty = statistics.prepareStatementCount
        deleteRankings()
        statistics.clear()
        assertEquals(5, feedPage("popular", viewerId, size = 5).postIds().size, "5개 페이지여야 한다")
        val five = statistics.prepareStatementCount

        assertNotNull(twenty.takeIf { it > 0 }, "쿼리 수를 셌어야 한다")
        assertEquals(five, twenty, "카드 조립의 쿼리 수가 게시물 수와 상관없어야 한다")
    }

    /** 리포스트를 count번 받은 원글. 리포스트한 사람은 모두 다르다 */
    private fun repostedPost(count: Int): String {
        val publicId = createdPublicId(onboardedUser())
        repeat(count) { repost(publicId, onboardedUser()).andExpect(status().isCreated) }
        return publicId
    }

    /** 리포스트 수를 통계 행에 바로 넣은 원글. 점수 순서만 보는 사례가 리포스트한 사람을 만들지 않게 한다 */
    private fun scoredPost(
        repostCount: Int,
        authorId: Long = onboardedUser(),
        body: String = postBody(),
    ): String {
        val publicId = createdPublicId(authorId, body)
        jdbcTemplate.update(
            "insert into sns_post_stats (post_id, repost_count, created_at, updated_at) " +
                "select id, ?, now(6), now(6) from sns_posts where public_id = ?",
            repostCount,
            publicId,
        )
        return publicId
    }

    private fun deleteRankings() {
        redisTemplate.keys("sns-popular:*").takeIf { it.isNotEmpty() }?.let { redisTemplate.delete(it) }
    }
}
