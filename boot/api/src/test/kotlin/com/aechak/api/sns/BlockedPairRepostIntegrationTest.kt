package com.aechak.api.sns

import com.jayway.jsonpath.JsonPath
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.HttpHeaders
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * 통합: 차단해도 두 사람이 서로의 글에 한 리포스트와 원글의 리포스트 수가 남고, 남은 리포스트가 있어도 두 사람이 피드, 상세, 프로필 탭,
 * 저장됨 탭, 팔로우 목록에서 상대의 글과 리포스트를 보지 못하며 차단을 풀면 남은 리포스트가 보통 규칙대로 다시 보이는지.
 * 깨지면 차단이 리포스트를 지우거나 수를 내리거나, 남은 리포스트를 거쳐 차단 관계인 상대의 글이 보이거나, 차단을 풀어도 리포스트가 돌아오지 않는다.
 */
class BlockedPairRepostIntegrationTest : SnsPostIntegrationTestBase() {
    @Autowired
    private lateinit var redisTemplate: StringRedisTemplate

    @Test
    fun `차단해도 두 사람이 서로의 글에 한 리포스트와 두 원글의 리포스트 수가 그대로다`() {
        val s = blockedAfterReposts()

        assertEquals(1, repostRowCount(s.me, s.otherPost), "내가 상대 글에 한 리포스트가 남아야 한다")
        assertEquals(1, repostRowCount(s.other, s.myPost), "상대가 내 글에 한 리포스트가 남아야 한다")
        assertEquals(2, repostCountOf(s.otherPost), "상대 글의 리포스트 수가 2 그대로여야 한다")
        assertEquals(2, repostCountOf(s.myPost), "내 글의 리포스트 수가 2 그대로여야 한다")
    }

    @Test
    fun `차단한 사람과 차단당한 사람 모두 팔로잉 피드에서 상대의 글과 상대의 리포스트를 보지 못한다`() {
        val s = blockedAfterReposts()

        assertEquals(
            listOf(s.myPost to publicIdOf(s.third)),
            feedPage("following", s.me).cards(),
            "상대 글이 내 리포스트나 제3자의 리포스트로 들어오거나 상대의 리포스트가 내 글의 리포스트 표시가 되면 안 된다",
        )
        assertEquals(
            listOf(s.otherPost to publicIdOf(s.third)),
            feedPage("following", s.other).cards(),
            "상대에게도 내 글이 들어오거나 내 리포스트가 상대 글의 리포스트 표시가 되면 안 된다",
        )
    }

    @Test
    fun `차단한 사람과 차단당한 사람 모두 탐색 피드와 인기 피드에서 상대의 글을 보지 못한다`() {
        val s = blockedAfterReposts()
        deleteRankings()

        assertEquals(emptyList<String>(), feedPage("explore", s.me).postIds(), "팔로우하지 않은 상대의 글도 탐색 피드에 나오면 안 된다")
        assertEquals(emptyList<String>(), feedPage("explore", s.other).postIds(), "상대의 탐색 피드에도 내 글이 나오면 안 된다")
        assertEquals(listOf(s.myPost), feedPage("popular", s.me).postIds(), "리포스트 수가 남은 상대 글도 인기 피드에 나오면 안 된다")
        assertEquals(listOf(s.otherPost), feedPage("popular", s.other).postIds(), "상대의 인기 피드에도 내 글이 나오면 안 된다")
    }

    @Test
    fun `차단한 사람과 차단당한 사람 모두 상대의 글 상세를 열지 못하고 리포스트 수는 누가 보든 그대로다`() {
        val s = blockedAfterReposts()

        getPost(s.otherPost, s.me)
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.errorCode").value(130001))
        getPost(s.myPost, s.other)
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.errorCode").value(130400))
        assertEquals(2, postDetail(s.otherPost, s.follower)["repostCount"], "다른 사람에게는 내 리포스트를 센 수가 보여야 한다")
        assertEquals(2, postDetail(s.myPost, s.me)["repostCount"], "내 글의 수에도 상대의 리포스트가 남아야 한다")
    }

    @Test
    fun `차단한 사람과 차단당한 사람 모두 상대의 프로필 탭을 열지 못하고 자기 리포스트 탭과 저장됨 탭에서 상대의 글이 빠진다`() {
        val s = blockedAfterReposts()

        TABS.forEach { tab ->
            getAs("/api/v1/sns/users/${publicIdOf(s.other)}/$tab", s.me)
                .andExpect(status().isForbidden)
                .andExpect(jsonPath("$.errorCode").value(130001))
            getAs("/api/v1/sns/users/${publicIdOf(s.me)}/$tab", s.other)
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.errorCode").value(130000))
        }
        assertEquals(emptyList<String>(), repostsTab(s.me, s.me), "내 리포스트 탭에서 상대 글이 빠져야 한다")
        assertEquals(emptyList<String>(), repostsTab(s.other, s.other), "상대의 리포스트 탭에서도 내 글이 빠져야 한다")
        assertEquals(emptyList<String>(), dataOf("/api/v1/sns/me/saves", s.me).ids(), "내 저장됨 탭에서 상대 글이 빠져야 한다")
        assertEquals(emptyList<String>(), dataOf("/api/v1/sns/me/saves", s.other).ids(), "상대의 저장됨 탭에서도 내 글이 빠져야 한다")
    }

    @Test
    fun `차단한 사람과 차단당한 사람 모두 제3자의 팔로워와 팔로잉 목록에서 상대를 보지 못한다`() {
        val s = blockedAfterReposts()
        val third = publicIdOf(s.third)

        listOf("followers", "followings").forEach { direction ->
            val path = "/api/v1/sns/users/$third/$direction"
            assertEquals(listOf(publicIdOf(s.me)), dataOf(path, s.me).ids(), "내게 보이는 $direction 목록에서 상대가 빠져야 한다")
            assertEquals(listOf(publicIdOf(s.other)), dataOf(path, s.other).ids(), "상대에게 보이는 $direction 목록에서 내가 빠져야 한다")
        }
    }

    @Test
    fun `내 팔로워는 차단 전에 내가 상대 글에 한 리포스트를 팔로잉 피드와 내 리포스트 탭에서 계속 본다`() {
        val s = blockedAfterReposts()

        assertEquals(
            listOf(s.otherPost to publicIdOf(s.me), s.myPost to null),
            feedPage("following", s.follower).cards(),
            "차단과 관계없는 내 팔로워에게는 내 리포스트가 그대로 나와야 한다",
        )
        assertEquals(listOf(s.otherPost), repostsTab(s.me, s.follower), "다른 사람에게는 내 리포스트 탭에 상대 글이 그대로 나와야 한다")
    }

    @Test
    fun `차단을 풀면 남은 리포스트가 팔로잉 피드, 리포스트 탭, 상세에 다시 나온다`() {
        val s = blockedAfterReposts()

        unrelate(s.me, s.other, "block")

        // 두 사람은 서로 팔로우하지 않아 상대 글은 자기 리포스트나 제3자의 리포스트로 들어온다
        assertEquals(
            listOf(s.otherPost to publicIdOf(s.me), s.myPost to publicIdOf(s.third)),
            feedPage("following", s.me).cards(),
            "내 리포스트로 상대 글이 다시 나와야 한다",
        )
        assertEquals(
            listOf(s.myPost to publicIdOf(s.other), s.otherPost to publicIdOf(s.third)),
            feedPage("following", s.other).cards(),
            "상대의 리포스트로 내 글이 다시 나와야 한다",
        )
        assertEquals(listOf(s.otherPost), repostsTab(s.me, s.me), "내 리포스트 탭에 상대 글이 다시 나와야 한다")
        assertEquals(listOf(s.myPost), repostsTab(s.other, s.other), "상대의 리포스트 탭에 내 글이 다시 나와야 한다")
        listOf(s.otherPost to s.me, s.myPost to s.other).forEach { (publicId, viewerId) ->
            val detail = postDetail(publicId, viewerId)
            assertEquals(true, detail["reposted"], "남은 리포스트가 상세의 reposted로 보여야 한다")
            assertEquals(2, detail["repostCount"], "리포스트 수가 2 그대로여야 한다")
        }
    }

    /**
     * 나, 상대, 둘이 팔로우하고 둘을 팔로우하는 제3자, 나를 팔로우하는 팔로워. 제3자가 두 원글을 리포스트한 뒤 내가 상대 글을, 상대가 내 글을
     * 리포스트하고 서로의 글을 저장한 다음 내가 상대를 차단한 상태다. 두 원글의 리포스트 수는 2다
     */
    private fun blockedAfterReposts(): Scenario {
        val me = onboardedUser()
        val other = onboardedUser()
        val third = onboardedUser()
        val follower = onboardedUser()
        listOf(me to third, other to third, third to me, third to other, follower to me).forEach { (actorId, targetId) ->
            relate(actorId, targetId, "follow")
        }
        val s = Scenario(me, other, third, follower, createdPublicId(me), createdPublicId(other))
        // 두 사람의 리포스트를 원글마다 가장 늦은 이벤트로 둬야 팔로잉 피드에 새면 리포스트 표시가 바뀐다
        listOf(third to s.myPost, third to s.otherPost, me to s.otherPost, other to s.myPost).forEach { (userId, publicId) ->
            repost(publicId, userId).andExpect(status().isCreated)
        }
        save(s.otherPost, me).andExpect(status().isCreated)
        save(s.myPost, other).andExpect(status().isCreated)
        relate(me, other, "block")
        return s
    }

    private fun getAs(
        path: String,
        viewerId: Long,
    ): ResultActions = mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer ${mintAccessToken(viewerId)}"))

    private fun dataOf(
        path: String,
        viewerId: Long,
    ): Map<String, Any?> {
        val json =
            getAs(path, viewerId)
                .andExpect(status().isOk)
                .andReturn()
                .response
                .getContentAsString(Charsets.UTF_8)
        return JsonPath.read(json, "$.data")
    }

    /** 주인의 리포스트 탭 칸에 든 원글 publicId */
    private fun repostsTab(
        ownerId: Long,
        viewerId: Long,
    ): List<String> = dataOf("/api/v1/sns/users/${publicIdOf(ownerId)}/reposts", viewerId).ids()

    /** 프로필 탭과 저장됨 탭은 게시물, 팔로우 목록은 사용자의 publicId다 */
    private fun Map<String, Any?>.ids(): List<String> = feedItems().map { it["publicId"] as String }

    /** 팔로잉 피드 카드마다 게시물 publicId와 리포스트한 사람의 publicId. 리포스트 표시가 없으면 null이다 */
    private fun Map<String, Any?>.cards(): List<Pair<String, String?>> =
        feedItems().map { (it["post"] as Map<*, *>)["publicId"] as String to (it["repostedBy"] as Map<*, *>?)?.get("publicId") as String? }

    // DB는 테스트마다 비우지만 Redis의 인기 순위는 남아서 같은 분에 앞 테스트가 남긴 순위를 지운다
    private fun deleteRankings() {
        redisTemplate.keys("sns-popular:*").takeIf { it.isNotEmpty() }?.let { redisTemplate.delete(it) }
    }

    private data class Scenario(
        val me: Long,
        val other: Long,
        val third: Long,
        val follower: Long,
        val myPost: String,
        val otherPost: String,
    )

    companion object {
        // 주인 판정이 같은 프로필 탭
        private val TABS = listOf("posts", "videos", "reposts")
    }
}
