package com.aechak.api.security

import com.aechak.api.sns.SnsPostIntegrationTestBase
import com.aechak.webcommon.http.CustomHttpHeaders
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * 통합 — 게시물 경로의 비로그인 접근. 쓰기는 모두 로그인이 필요하다.
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
}
