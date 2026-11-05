package com.aechak.api.sns

import com.jayway.jsonpath.JsonPath
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 통합: 게시와 수정이 해시태그를 정규화해 저장하고 게시, 수정, 삭제가 태그 게시글 수를 맞추는지, 상세가 작성자가 단 순서대로 태그를 싣는지,
 * 자동완성이 정규화한 검색어의 상태와 게시글 수가 많은 태그를 주는지.
 * 깨지면 표기만 다른 태그가 따로 세어지거나 규칙을 어긴 태그가 저장되거나 태그 게시글 수가 실제 게시물 수와 어긋나거나
 * 해시태그 모달이 지운 게시물의 태그를 추천하거나 이미 있는 태그를 새 태그로 안내한다.
 */
class HashtagIntegrationTest : SnsPostIntegrationTestBase() {
    @Test
    fun `표기만 다른 태그는 같은 태그 하나로 저장한다`() {
        val userId = onboardedUser()

        val publicId = createdPublicId(userId, postBody(hashtags = listOf("#DOG", "dog")))
        createdPublicId(userId, postBody(hashtags = listOf(" Dog ")))

        assertEquals(listOf("dog"), postDetail(publicId)["hashtags"])
        assertEquals(mapOf("dog" to 2), hashtagCounts())
    }

    @Test
    fun `게시하면 새 태그 행을 만들고 있던 태그는 수를 1 올린다`() {
        val userId = onboardedUser()
        createdPublicId(userId, postBody(hashtags = listOf("산책")))

        createdPublicId(userId, postBody(hashtags = listOf("산책", "골든리트리버")))

        assertEquals(mapOf("산책" to 2, "골든리트리버" to 1), hashtagCounts())
    }

    @Test
    fun `캡션 속 #단어는 태그로 등록하지 않는다`() {
        val userId = onboardedUser()

        val publicId = createdPublicId(userId, postBody(caption = "오늘 #산책 다녀왔어요"))

        assertEquals(emptyList<Any>(), postDetail(publicId)["hashtags"])
        assertTrue(hashtagCounts().isEmpty())
    }

    @Test
    fun `상세는 작성자가 단 순서대로 태그를 싣는다`() {
        val userId = onboardedUser()
        // 태그 행은 이름 순서로 만들어지므로 그 반대 순서로 단다
        val tags = listOf("코코", "산책", "골든리트리버", "ㅋㅋㅋ")

        val publicId = createdPublicId(userId, postBody(hashtags = tags))

        assertEquals(tags, postDetail(publicId)["hashtags"])
    }

    @Test
    fun `태그가 합친 뒤 30개를 넘으면 130406이고 승격하지 않는다`() {
        val userId = onboardedUser()

        createPost(userId, postBody(photoKeys = listOf(tmpPhotoKey(userId)), hashtags = (1..31).map { "tag$it" }))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(130406))
            .andExpect(jsonPath("$.message").value("해시태그는 30개까지 달 수 있습니다."))

        assertTrue(fakeFileStorage.promotedKeys.isEmpty())
        assertEquals(0, postCountOf(userId))
    }

    @Test
    fun `쓸 수 없는 글자가 든 태그는 130500이다`() {
        createPost(onboardedUser(), postBody(hashtags = listOf("산책 가자")))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(130500))
            .andExpect(jsonPath("$.message").value("해시태그에 쓸 수 없는 글자가 있습니다."))
    }

    @Test
    fun `30자를 넘는 태그는 130501이다`() {
        createPost(onboardedUser(), postBody(hashtags = listOf("가".repeat(31))))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(130501))
            .andExpect(jsonPath("$.message").value("해시태그는 30자까지 쓸 수 있습니다."))
    }

    @Test
    fun `금지어가 든 태그는 130502이고 게시물과 태그 행과 승격이 모두 없다`() {
        val userId = onboardedUser()

        createPost(userId, postBody(photoKeys = listOf(tmpPhotoKey(userId)), hashtags = listOf("산책", "시발")))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(130502))
            .andExpect(jsonPath("$.message").value("해시태그에 쓸 수 없는 단어가 있습니다."))

        assertEquals(0, postCountOf(userId))
        assertTrue(hashtagCounts().isEmpty())
        assertTrue(fakeFileStorage.promotedKeys.isEmpty())
    }

    @Test
    fun `수정하면 빠진 태그 수를 내리고 새 태그 수를 올리며 남은 태그 수는 그대로다`() {
        val userId = onboardedUser()
        val publicId = createdPublicId(userId, postBody(hashtags = listOf("산책", "한강")))
        createdPublicId(userId, postBody(hashtags = listOf("산책")))

        updatePost(publicId, userId, updateBody(hashtags = listOf("한강", "#코코"))).andExpect(status().isNoContent)

        assertEquals(mapOf("산책" to 1, "한강" to 1, "코코" to 1), hashtagCounts())
        assertEquals(listOf("한강", "코코"), postDetail(publicId)["hashtags"])
    }

    @Test
    fun `지우면 그 게시물의 태그 수를 1 내리고 태그 연결을 지운다`() {
        val userId = onboardedUser()
        val publicId = createdPublicId(userId, postBody(hashtags = listOf("산책", "한강")))
        createdPublicId(userId, postBody(hashtags = listOf("산책")))

        deletePost(publicId, userId).andExpect(status().isNoContent)

        assertEquals(mapOf("산책" to 1, "한강" to 0), hashtagCounts())
        assertEquals(0, hashtagLinkCountOf(publicId))
    }

    @Test
    fun `이미 지운 게시물을 다시 지워도 태그 수를 다시 내리지 않는다`() {
        val userId = onboardedUser()
        val publicId = createdPublicId(userId, postBody(hashtags = listOf("산책")))
        createdPublicId(userId, postBody(hashtags = listOf("산책")))
        deletePost(publicId, userId).andExpect(status().isNoContent)

        deletePost(publicId, userId).andExpect(status().isNoContent)

        assertEquals(mapOf("산책" to 1), hashtagCounts())
    }

    @Test
    fun `수정에 태그 31개를 보내면 130406이고 금지어 태그는 130502이며 태그는 그대로다`() {
        val userId = onboardedUser()
        val publicId = createdPublicId(userId, postBody(hashtags = listOf("산책")))

        updatePost(publicId, userId, updateBody(hashtags = (1..31).map { "tag$it" }))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(130406))
        updatePost(publicId, userId, updateBody(hashtags = listOf("한강", "시발")))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(130502))

        assertEquals(listOf("산책"), postDetail(publicId)["hashtags"])
        assertEquals(mapOf("산책" to 1), hashtagCounts())
    }

    @Test
    fun `글 게시물의 캡션을 비우는 수정은 130402이고 태그와 태그 수는 그대로다`() {
        val userId = onboardedUser()
        val publicId = createdPublicId(userId, postBody(hashtags = listOf("산책")))

        updatePost(publicId, userId, updateBody(caption = null, hashtags = listOf("한강")))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(130402))

        assertEquals(listOf("산책"), postDetail(publicId)["hashtags"])
        assertEquals(mapOf("산책" to 1), hashtagCounts())
    }

    @Test
    fun `없는 게시물에 태그 31개로 수정하면 404가 아니라 400 130406이다`() {
        updatePost("01JF9W3Q6R8T2V4X7Z9B1C3D5J", onboardedUser(), updateBody(hashtags = (1..31).map { "tag$it" }))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(130406))
    }

    @Test
    fun `자동완성은 이름에 검색어가 든 태그를 게시글 수가 많은 순, 같으면 이름 순으로 주고 같은 이름의 태그가 없으면 NEW다`() {
        val userId = onboardedUser()
        postTimes(userId, 3, "산책스타그램")
        postTimes(userId, 2, "한강산책", "봄산책")
        postTimes(userId, 1, "아침산책길", "댕댕이")

        val result = searchHashtags(userId, "산책")

        assertEquals("NEW", result["status"])
        assertNull(result["exact"])
        assertEquals(
            listOf(tag("산책스타그램", 3), tag("봄산책", 2), tag("한강산책", 2), tag("아침산책길", 1)),
            result["items"],
        )
    }

    @Test
    fun `자동완성에 맞는 태그가 없으면 NEW와 빈 items다`() {
        val userId = onboardedUser()
        postTimes(userId, 1, "산책")

        assertEquals(searchResult("콩이", "NEW"), searchHashtags(userId, "콩이"))
    }

    @Test
    fun `검색어가 없거나 #만 있으면 EMPTY와 게시글 수가 많은 추천 태그다`() {
        val userId = onboardedUser()
        postTimes(userId, 3, "댕댕이")
        postTimes(userId, 2, "산책스타그램")
        postTimes(userId, 1, "코코")
        val popular = listOf(tag("댕댕이", 3), tag("산책스타그램", 2), tag("코코", 1))

        assertEquals(searchResult("", "EMPTY", items = popular), searchHashtags(userId))
        assertEquals(searchResult("", "EMPTY", items = popular), searchHashtags(userId, ""))
        assertEquals(searchResult("", "EMPTY", items = popular.take(2)), searchHashtags(userId, " # ", size = 2))
    }

    @Test
    fun `검색어의 밑줄은 와일드카드가 아니라 글자 그대로 찾는다`() {
        val userId = onboardedUser()
        postTimes(userId, 1, "dog_walk", "dogxwalk")

        assertEquals(listOf(tag("dog_walk", 1)), searchHashtags(userId, "g_w")["items"])
    }

    @Test
    fun `같은 이름의 태그가 있으면 EXISTS이고 그 태그는 exact로만 온다`() {
        val userId = onboardedUser()
        postTimes(userId, 2, "산책스타그램")
        postTimes(userId, 1, "산책", "아침산책")

        assertEquals(
            searchResult("산책", "EXISTS", exact = tag("산책", 1), items = listOf(tag("산책스타그램", 2), tag("아침산책", 1))),
            searchHashtags(userId, "#산책"),
        )
    }

    @Test
    fun `같은 글자가 든 태그 21개보다 수가 적은 태그도 정확히 검색하면 exact로 온다`() {
        val userId = onboardedUser()
        val popular = (1..21).map { "산책$it" }
        postTimes(userId, 2, *popular.toTypedArray())
        postTimes(userId, 1, "산책")

        assertEquals(
            searchResult("산책", "EXISTS", exact = tag("산책", 1), items = popular.sorted().take(20).map { tag(it, 2) }),
            searchHashtags(userId, "산책"),
        )
    }

    @Test
    fun `허용 밖 글자는 INVALID이고 30자를 넘으면 TOO_LONG이며 items는 비어 있다`() {
        val userId = onboardedUser()
        postTimes(userId, 1, "산책", "가".repeat(30))

        assertEquals(searchResult("산책!", "INVALID"), searchHashtags(userId, "#산책!"))
        assertEquals(searchResult("가".repeat(31), "TOO_LONG"), searchHashtags(userId, "가".repeat(31)))
    }

    @Test
    fun `금지어가 든 검색어는 BANNED이고 items는 부분 일치 태그다`() {
        val userId = onboardedUser()
        // 시발점은 금지어 예외 단어라 태그로 달 수 있다
        postTimes(userId, 1, "시발점")

        assertEquals(searchResult("시발", "BANNED", items = listOf(tag("시발점", 1))), searchHashtags(userId, "시발"))
    }

    @Test
    fun `검색만 하고 게시하지 않은 새 태그는 다른 사용자의 자동완성에 나오지 않는다`() {
        assertEquals("NEW", searchHashtags(onboardedUser(), "콩이산책")["status"])

        assertEquals(searchResult("콩이", "NEW"), searchHashtags(onboardedUser(), "콩이"))
        assertTrue(hashtagCounts().isEmpty())
    }

    @Test
    fun `마지막 게시물이 지워진 태그는 자동완성에서 빠지고 NEW이며 다시 달면 수가 1이다`() {
        val userId = onboardedUser()
        val publicId = createdPublicId(userId, postBody(hashtags = listOf("산책")))
        postTimes(userId, 1, "산책로")
        deletePost(publicId, userId).andExpect(status().isNoContent)

        assertEquals(searchResult("산책", "NEW", items = listOf(tag("산책로", 1))), searchHashtags(userId, "산책"))

        postTimes(userId, 1, "산책")
        assertEquals(tag("산책", 1), searchHashtags(userId, "산책")["exact"])
    }

    @Test
    fun `뮤트한 사용자의 게시물도 게시글 수에 들어간다`() {
        val viewerId = onboardedUser()
        val mutedId = onboardedUser()
        relate(viewerId, mutedId, "mute")
        postTimes(mutedId, 1, "산책")
        postTimes(viewerId, 1, "산책")

        assertEquals(tag("산책", 2), searchHashtags(viewerId, "산책")["exact"])
    }

    @Test
    fun `나를 차단한 사용자의 게시물에만 달린 태그도 게시글 수와 함께 나온다`() {
        val viewerId = onboardedUser()
        val blockerId = onboardedUser()
        relate(blockerId, viewerId, "block")
        postTimes(blockerId, 1, "콩이산책")

        assertEquals(listOf(tag("콩이산책", 1)), searchHashtags(viewerId, "콩이")["items"])
    }

    @Test
    fun `자동완성 size가 1부터 50까지를 벗어나면 400 90001이다`() {
        val userId = onboardedUser()

        listOf(0, 51).forEach { size ->
            search(userId, "산책", size)
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.errorCode").value(90001))
        }
    }

    private fun hashtagLinkCountOf(publicId: String): Int =
        jdbcTemplate.queryForObject(
            "select count(*) from sns_post_hashtags h join sns_posts p on p.id = h.post_id where p.public_id = ?",
            Int::class.java,
            publicId,
        )!!

    /** 같은 태그를 단 게시물을 times개 게시한다. */
    private fun postTimes(
        userId: Long,
        times: Int,
        vararg hashtags: String,
    ) = repeat(times) { createdPublicId(userId, postBody(hashtags = hashtags.toList())) }

    private fun search(
        viewerId: Long,
        query: String? = null,
        size: Int? = null,
    ): ResultActions {
        val request = get("/api/v1/sns/hashtags").header(HttpHeaders.AUTHORIZATION, "Bearer ${mintAccessToken(viewerId)}")
        if (query != null) request.param("query", query)
        if (size != null) request.param("size", size.toString())
        return mockMvc.perform(request)
    }

    /** 자동완성을 200으로 받고 data 객체를 돌려준다. */
    private fun searchHashtags(
        viewerId: Long,
        query: String? = null,
        size: Int? = null,
    ): Map<String, Any?> {
        val json =
            search(viewerId, query, size)
                .andExpect(status().isOk)
                .andReturn()
                .response
                .getContentAsString(Charsets.UTF_8)
        return JsonPath.read(json, "$.data")
    }

    private fun searchResult(
        query: String,
        status: String,
        exact: Map<String, Any>? = null,
        items: List<Map<String, Any>> = emptyList(),
    ): Map<String, Any?> = mapOf("query" to query, "status" to status, "exact" to exact, "items" to items)

    private fun tag(
        name: String,
        postCount: Int,
    ): Map<String, Any> = mapOf("name" to name, "postCount" to postCount)
}
