package com.aechak.api.sns

import com.aechak.domain.user.user.User
import com.jayway.jsonpath.JsonPath
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.HttpHeaders
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Duration
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 통합: 게시물 검색과 해시태그 게시물 목록이 캡션과 해시태그로 원글을 찾고(닉네임으로는 찾지 않고) 인기순과 최신순으로 주며
 * 결과 수는 보는 사람의 차단과 뮤트로 바꾸지 않고 목록에서만 숨기는지, 인기순 다음 페이지가 첫 페이지의 순위를 따르는지.
 * 깨지면 검색에 차단하거나 뮤트한 사용자의 글, 리포스트, 지운 글이 나오거나 결과 수가 사람마다 다르거나 내리는 동안 글이 겹치고 빠진다.
 */
class SnsSearchPostIntegrationTest : SnsPostIntegrationTestBase() {
    @Autowired
    private lateinit var redisTemplate: StringRedisTemplate

    // DB는 테스트마다 비우지만 Redis는 남아서 같은 분에 앞 테스트가 남긴 순위를 지운다
    @BeforeEach
    fun clearRankings() {
        redisTemplate.keys("sns-search-popular:*").takeIf { it.isNotEmpty() }?.let { redisTemplate.delete(it) }
    }

    @Test
    fun `캡션이나 해시태그에 단어가 모두 든 원글을 찾고 닉네임만 맞는 글은 찾지 않는다`() {
        val authorId = onboardedUser()
        val captionOnly = createdPublicId(authorId, postBody(caption = "한강 산책 다녀왔어요"))
        val tagOnly = createdPublicId(authorId, postBody(caption = "오늘 날씨", hashtags = listOf("한강산책")))
        val split = createdPublicId(authorId, postBody(caption = "한강에서", hashtags = listOf("산책")))
        val writtenHash = createdPublicId(authorId, postBody(caption = "#산책 기록 한강"))
        createdPublicId(authorId, postBody(caption = "한강만 있는 글"))
        val nicknameMatch = onboardedUser()
        jdbcTemplate.update("update user_profiles set nickname = '한강산책메이트' where user_id = ?", nicknameMatch)
        createdPublicId(nicknameMatch, postBody(caption = "아무 말"))

        val page = searchPage("한강 산책", sort = "LATEST")

        assertEquals(listOf(writtenHash, split, tagOnly, captionOnly), page.postIds(), "두 단어가 캡션과 태그 어디에든 모두 든 글만 나와야 한다")
        assertEquals(4, (page["totalCount"] as Number).toInt(), "결과 수는 맞는 글 수여야 한다")
    }

    @Test
    fun `앞의 #를 떼고 찾고 영문 대소문자를 가리지 않는다`() {
        val authorId = onboardedUser()
        val tagged = createdPublicId(authorId, postBody(caption = "산책", hashtags = listOf("Dog")))
        val caption = createdPublicId(authorId, postBody(caption = "my DOG walk"))

        assertEquals(listOf(caption, tagged), searchPage("#dog", sort = "LATEST").postIds(), "#를 떼고 대소문자 없이 찾아야 한다")
    }

    @Test
    fun `#만 입력하거나 공백뿐이면 빈 결과와 결과 수 0이다`() {
        createdPublicId(onboardedUser())

        for (keyword in listOf("#", "   ")) {
            val page = searchPage(keyword)
            assertTrue(page.feedItems().isEmpty(), "검색하지 않아야 한다: '$keyword'")
            assertEquals(0, (page["totalCount"] as Number).toInt(), "결과 수는 0이어야 한다: '$keyword'")
        }
    }

    @Test
    fun `맨 앞의 # 하나만 떼고 둘째 단어의 #는 캡션 글자나 태그로 찾는다`() {
        val authorId = onboardedUser()
        val tagged = createdPublicId(authorId, postBody(caption = "dog", hashtags = listOf("cat")))
        val written = createdPublicId(authorId, postBody(caption = "dog #cat"))
        createdPublicId(authorId, postBody(caption = "dog cat"))

        assertEquals(listOf(written, tagged), searchPage("dog #cat", sort = "LATEST").postIds(), "#cat은 캡션의 #cat이나 cat 태그와만 맞아야 한다")
    }

    @Test
    fun `캡션 단어가 다르면 태그 표기가 같아도 인기순 순위를 나눠 쓰지 않는다`() {
        val dog = createdPublicId(onboardedUser(), postBody(caption = "dog"))

        assertEquals(listOf(dog), searchPage("dog").postIds())
        val doubled = searchPage("##dog")

        assertTrue(doubled.postIds().isEmpty(), "##dog는 캡션의 #dog를 찾으므로 dog 순위를 쓰면 안 된다")
        assertEquals(0, (doubled["totalCount"] as Number).toInt())
    }

    @Test
    fun `없는 태그의 목록도 잘못된 커서는 90002다`() {
        mockMvc
            .perform(get("/api/v1/sns/hashtags/{name}/posts", "없는태그").param("cursor", "garbage"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(90002))
    }

    @Test
    fun `차단 양방향과 뮤트 작성자의 글은 목록에서만 빠지고 결과 수는 그대로다`() {
        val viewerId = onboardedUser()
        val blockedByMe = onboardedUser()
        val blockingMe = onboardedUser()
        val muted = onboardedUser()
        val visible = createdPublicId(onboardedUser(), postBody(caption = "급식기 후기"))
        val blockedPost = createdPublicId(blockedByMe, postBody(caption = "급식기 후기"))
        val blockingPost = createdPublicId(blockingMe, postBody(caption = "급식기 후기"))
        val mutedPost = createdPublicId(muted, postBody(caption = "급식기 후기"))
        relate(viewerId, blockedByMe, "block")
        relate(blockingMe, viewerId, "block")
        relate(viewerId, muted, "mute")

        val mine = searchPage("급식기", viewerId, sort = "LATEST")
        val guest = searchPage("급식기", sort = "LATEST")

        assertEquals(listOf(visible), mine.postIds(), "차단과 뮤트 작성자의 글이 빠져야 한다")
        assertEquals(4, (mine["totalCount"] as Number).toInt(), "결과 수는 보는 사람의 숨김과 상관없어야 한다")
        assertEquals(listOf(mutedPost, blockingPost, blockedPost, visible), guest.postIds(), "비로그인은 모두 받아야 한다")
    }

    @Test
    fun `지운 글, 운영자가 지운 글, 탈퇴 작성자의 글은 목록과 결과 수에서 빠지고 리포스트는 원글 한 번이다`() {
        val authorId = onboardedUser()
        val withdrawn = onboardedUser()
        val kept = createdPublicId(authorId, postBody(caption = "보리 산책"))
        val deleted = createdPublicId(authorId, postBody(caption = "보리 산책"))
        deletePost(deleted, authorId).andExpect(status().isNoContent)
        val operatorDeleted = createdPublicId(authorId, postBody(caption = "보리 산책"))
        jdbcTemplate.update("update sns_posts set status = 'DELETED', deletion_reason = 'OPERATOR' where public_id = ?", operatorDeleted)
        createdPublicId(withdrawn, postBody(caption = "보리 산책"))
        tx.execute { em.find(User::class.java, withdrawn).withdraw() }
        repost(kept, onboardedUser()).andExpect(status().isCreated)

        for (sort in listOf("POPULAR", "LATEST")) {
            val page = searchPage("보리", sort = sort)
            assertEquals(listOf(kept), page.postIds(), "$sort 결과에는 남은 원글만 한 번 나와야 한다")
            assertEquals(1, (page["totalCount"] as Number).toInt(), "$sort 결과 수에서도 빠져야 한다")
        }
    }

    @Test
    fun `인기순은 기간 제한 없이 점수가 높은 글부터 주고 점수가 0인 글도 최신순으로 잇는다`() {
        val zeroOld = scoredPost(0, "산책 기록")
        val high = scoredPost(3, "산책 기록")
        val low = scoredPost(1, "산책 기록")
        val zeroNew = scoredPost(0, "산책 기록")
        backdatePost(high, LocalDateTime.now().minusDays(10))
        backdatePost(zeroOld, LocalDateTime.now().minusDays(1))

        val popular = searchPage("산책")
        val latest = searchPage("산책", sort = "LATEST")

        assertEquals(listOf(high, low, zeroNew, zeroOld), popular.postIds(), "열흘 전 글도 점수순으로 먼저 나와야 한다")
        assertEquals(listOf(zeroNew, low, zeroOld, high), latest.postIds(), "최신순은 작성 시각순이어야 한다")
        assertEquals(popular["totalCount"], latest["totalCount"], "정렬을 바꿔도 결과 수는 같아야 한다")
    }

    @Test
    fun `인기순 다음 페이지는 첫 페이지의 순위를 따르고 최신순은 이어서 준다`() {
        val posts = List(5) { scoredPost(it, "코코 사진") }

        val first = searchPage("코코", size = 2)
        posts.forEach { setScore(it, 10) }
        val second = searchPage("코코", cursor = first["nextCursor"] as String, size = 2)
        assertNull(second["totalCount"], "다음 페이지는 결과 수를 싣지 않는다")
        val third = searchPage("코코", cursor = second["nextCursor"] as String, size = 2)

        assertEquals(posts.reversed(), first.postIds() + second.postIds() + third.postIds(), "점수가 바뀌어도 첫 페이지의 순위대로 겹침 없이 이어야 한다")
        assertEquals(false, third["hasNext"])

        val latestFirst = searchPage("코코", sort = "LATEST", size = 3)
        val latestNext = searchPage("코코", sort = "LATEST", cursor = latestFirst["nextCursor"] as String, size = 3)
        assertEquals(posts.reversed(), latestFirst.postIds() + latestNext.postIds(), "최신순도 겹침 없이 이어야 한다")
    }

    @Test
    fun `다른 검색어나 다른 정렬의 커서는 90002다`() {
        repeat(3) { scoredPost(1, "보리 코코") }
        val popularCursor = searchPage("보리", size = 1)["nextCursor"] as String
        val latestCursor = searchPage("보리", sort = "LATEST", size = 1)["nextCursor"] as String

        search("코코", cursor = popularCursor).andExpect(status().isBadRequest).andExpect(jsonPath("$.errorCode").value(90002))
        search(
            "보리",
            sort = "LATEST",
            cursor = popularCursor,
        ).andExpect(status().isBadRequest).andExpect(jsonPath("$.errorCode").value(90002))
        search(
            "코코",
            sort = "LATEST",
            cursor = latestCursor,
        ).andExpect(status().isBadRequest).andExpect(jsonPath("$.errorCode").value(90002))
    }

    @Test
    fun `100자를 넘는 검색어와 모르는 정렬은 90001이다`() {
        search("가".repeat(101)).andExpect(status().isBadRequest).andExpect(jsonPath("$.errorCode").value(90001))
        search("산책", sort = "RELEVANCE").andExpect(status().isBadRequest).andExpect(jsonPath("$.errorCode").value(90001))
    }

    @Test
    fun `해시태그 게시물 목록은 정확히 같은 태그의 글만 주고 헤더의 게시글 수는 보는 사람의 숨김과 상관없다`() {
        val viewerId = onboardedUser()
        val muted = onboardedUser()
        val exact = createdPublicId(onboardedUser(), postBody(caption = "봄", hashtags = listOf("봄산책")))
        val mutedPost = createdPublicId(muted, postBody(caption = "봄", hashtags = listOf("봄산책")))
        createdPublicId(onboardedUser(), postBody(caption = "봄", hashtags = listOf("봄산책스타그램")))
        createdPublicId(onboardedUser(), postBody(caption = "#봄산책"))
        relate(viewerId, muted, "mute")

        val mine = hashtagPage("#봄산책", viewerId, sort = "LATEST")
        val guest = hashtagPage("봄산책")

        assertEquals(listOf(exact), mine.postIds(), "뮤트한 작성자의 글과 다른 태그, 캡션에만 쓴 태그는 빠져야 한다")
        assertEquals(mapOf("name" to "봄산책", "postCount" to 2), mine["hashtag"], "헤더는 태그의 게시글 수 그대로여야 한다")
        assertEquals(setOf(exact, mutedPost), guest.postIds().toSet(), "비로그인은 그 태그의 글을 모두 받아야 한다")
    }

    @Test
    fun `없는 태그와 글이 모두 지워진 태그는 게시글 수 0과 빈 목록이다`() {
        val authorId = onboardedUser()
        val publicId = createdPublicId(authorId, postBody(caption = "월드컵", hashtags = listOf("댕댕월드컵")))
        deletePost(publicId, authorId).andExpect(status().isNoContent)

        val gone = hashtagPage("댕댕월드컵")
        val unknown = hashtagPage("없는태그")

        assertEquals(mapOf("name" to "댕댕월드컵", "postCount" to 0), gone["hashtag"])
        assertTrue(gone.feedItems().isEmpty())
        assertEquals(mapOf("name" to "없는태그", "postCount" to 0), unknown["hashtag"])
        assertTrue(unknown.feedItems().isEmpty())
    }

    @Test
    fun `로그인 사용자의 게시물 검색 첫 페이지는 입력한 검색어 그대로 최근 검색어에 남는다`() {
        val viewerId = onboardedUser()
        createdPublicId(onboardedUser(), postBody(caption = "봄산책", hashtags = listOf("봄산책")))

        searchPage("#봄산책", viewerId)

        await().atMost(Duration.ofSeconds(5)).untilAsserted {
            assertEquals(listOf("#봄산책"), recentKeywords(viewerId), "#를 떼지 않은 입력값이 남아야 한다")
        }
    }

    @Test
    fun `다음 페이지, 해시태그 게시물 목록, 계정과 해시태그 세그먼트, 비로그인 검색은 최근 검색어에 남지 않는다`() {
        val viewerId = onboardedUser()
        repeat(2) { createdPublicId(onboardedUser(), postBody(caption = "산책", hashtags = listOf("코코"))) }
        val cursor = searchPage("산책", size = 1)["nextCursor"] as String

        searchPage("산책", viewerId, cursor = cursor, size = 1)
        hashtagPage("코코", viewerId)
        mockMvc
            .perform(
                get(
                    "/api/v1/sns/search/users",
                ).param("keyword", "보리").header(HttpHeaders.AUTHORIZATION, "Bearer ${mintAccessToken(viewerId)}"),
            ).andExpect(status().isOk)
        mockMvc
            .perform(
                get(
                    "/api/v1/sns/search/hashtags",
                ).param("keyword", "보리").header(HttpHeaders.AUTHORIZATION, "Bearer ${mintAccessToken(viewerId)}"),
            ).andExpect(status().isOk)
        // 앞 요청들이 적재했다면 이 검색의 적재를 기다리는 동안 함께 남는다
        searchPage("마지막", viewerId)

        await().atMost(Duration.ofSeconds(5)).untilAsserted {
            assertEquals(listOf("마지막"), recentKeywords(viewerId), "첫 페이지 게시물 검색만 남아야 한다")
        }
        assertEquals(0, jdbcTemplate.queryForObject("select count(*) from recent_searches where user_id <> ?", Int::class.java, viewerId))
    }

    private fun recentKeywords(userId: Long): List<String> =
        jdbcTemplate.queryForList(
            "select keyword from recent_searches where user_id = ? order by searched_at desc, id desc",
            String::class.java,
            userId,
        )

    private fun scoredPost(
        repostCount: Int,
        caption: String,
    ): String {
        val publicId = createdPublicId(onboardedUser(), postBody(caption = caption))
        setScore(publicId, repostCount)
        return publicId
    }

    /** 점수 순서만 보는 사례가 리포스트한 사람을 만들지 않게 통계 행에 바로 넣는다 */
    private fun setScore(
        publicId: String,
        repostCount: Int,
    ) {
        jdbcTemplate.update(
            "insert into sns_post_stats (post_id, repost_count, created_at, updated_at) select id, ?, now(6), now(6) from sns_posts " +
                "where public_id = ? on duplicate key update repost_count = values(repost_count)",
            repostCount,
            publicId,
        )
    }

    private fun search(
        keyword: String,
        viewerId: Long? = null,
        sort: String? = null,
        cursor: String? = null,
        size: Int? = null,
    ): ResultActions {
        val request = get("/api/v1/sns/search/posts").param("keyword", keyword)
        sort?.let { request.param("sort", it) }
        cursor?.let { request.param("cursor", it) }
        size?.let { request.param("size", it.toString()) }
        if (viewerId != null) request.header(HttpHeaders.AUTHORIZATION, "Bearer ${mintAccessToken(viewerId)}")
        return mockMvc.perform(request)
    }

    private fun searchPage(
        keyword: String,
        viewerId: Long? = null,
        sort: String? = null,
        cursor: String? = null,
        size: Int? = null,
    ): Map<String, Any?> = data(search(keyword, viewerId, sort, cursor, size))

    private fun hashtagPage(
        name: String,
        viewerId: Long? = null,
        sort: String? = null,
    ): Map<String, Any?> {
        val request = get("/api/v1/sns/hashtags/{name}/posts", name)
        sort?.let { request.param("sort", it) }
        if (viewerId != null) request.header(HttpHeaders.AUTHORIZATION, "Bearer ${mintAccessToken(viewerId)}")
        return data(mockMvc.perform(request))
    }

    private fun data(result: ResultActions): Map<String, Any?> =
        JsonPath.read(
            result
                .andExpect(status().isOk)
                .andReturn()
                .response
                .getContentAsString(Charsets.UTF_8),
            "$.data",
        )
}
