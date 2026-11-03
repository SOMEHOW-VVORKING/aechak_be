package com.aechak.api.security

import com.aechak.api.sns.SnsPostIntegrationTestBase
import com.aechak.domain.user.user.User
import com.aechak.webcommon.http.CustomHttpHeaders
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.assertEquals

/**
 * 통합 — 게시물 경로의 비로그인 접근. 상세만 비로그인에게 열고 쓰기는 모두 로그인이 필요하다.
 */
class GuestSnsPostAccessIntegrationTest : SnsPostIntegrationTestBase() {
    @Test
    fun `토큰 없이 게시하면 401 20004다`() {
        mockMvc
            .perform(
                post("/api/v1/sns/posts")
                    .header(CustomHttpHeaders.IDEMPOTENCY_KEY, "guest-key")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(postBody()),
            ).andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.errorCode").value(20004))
    }

    @Test
    fun `토큰 없이 상세를 부르면 200이고 보는 사람 상태가 모두 false다`() {
        val publicId = createdPublicId(onboardedUser())

        val detail = postDetail(publicId)

        assertEquals(false, detail["isMine"])
        assertEquals(false, detail["following"])
        assertEquals(false, detail["mutedByMe"])
    }

    @Test
    fun `온보딩 전 사용자가 토큰을 붙여 상세를 부르면 403 20006이다`() {
        val publicId = createdPublicId(onboardedUser())
        val pendingId = tx.execute { User.preRegister().also { em.persist(it) }.id }!!

        getPost(publicId, pendingId)
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.errorCode").value(20006))
    }
}
