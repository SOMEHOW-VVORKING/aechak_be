package com.aechak.api.sns

import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 통합: 게시와 수정이 해시태그를 정규화해 저장하고 게시, 수정, 삭제가 태그 게시글 수를 맞추는지, 상세가 작성자가 단 순서대로 태그를 싣는지.
 * 깨지면 표기만 다른 태그가 따로 세어지거나 규칙을 어긴 태그가 저장되거나 태그 게시글 수가 실제 게시물 수와 어긋난다.
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

    private fun hashtagLinkCountOf(publicId: String): Int =
        jdbcTemplate.queryForObject(
            "select count(*) from sns_post_hashtags h join sns_posts p on p.id = h.post_id where p.public_id = ?",
            Int::class.java,
            publicId,
        )!!
}
