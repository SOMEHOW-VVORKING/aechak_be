package com.aechak.api.sns

import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 통합: 게시가 해시태그를 정규화해 저장하고 태그 게시글 수를 올리는지, 상세가 작성자가 단 순서대로 태그를 싣는지.
 * 깨지면 표기만 다른 태그가 따로 세어지거나 규칙을 어긴 태그가 저장된다.
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
}
