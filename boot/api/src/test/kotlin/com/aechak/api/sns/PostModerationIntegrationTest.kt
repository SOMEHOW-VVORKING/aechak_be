package com.aechak.api.sns

import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 통합 — 게시물 캡션이 리뷰와 같은 금지어 기준으로 저장되거나 거부되는지. 실제 아호코라식 스캐너를 쓴다.
 */
class PostModerationIntegrationTest : SnsPostIntegrationTestBase() {
    @Test
    fun `금지어가 없는 캡션은 그대로 저장한다`() {
        val userId = onboardedUser()

        val publicId = createdPublicId(userId, postBody(caption = "오늘 코코랑 한강 산책"))

        assertEquals("오늘 코코랑 한강 산책", postRow(publicId)["caption"])
    }

    @Test
    fun `금지어 비율이 절반 이하인 캡션은 가린 글로 저장한다`() {
        val userId = onboardedUser()

        val publicId = createdPublicId(userId, postBody(caption = "시발 산책은 좋아요"))

        assertEquals("** 산책은 좋아요", postRow(publicId)["caption"])
    }

    @Test
    fun `금지어 비율이 절반을 넘으면 130405이고 게시물과 미디어와 승격이 모두 없다`() {
        val userId = onboardedUser()

        createPost(userId, postBody(caption = "시발 씨발 좆!!!", photoKeys = listOf(tmpPhotoKey(userId))))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(130405))

        assertEquals(0, postCountOf(userId))
        assertEquals(0, mediaRowCount())
        assertTrue(fakeFileStorage.promotedKeys.isEmpty())
    }
}
