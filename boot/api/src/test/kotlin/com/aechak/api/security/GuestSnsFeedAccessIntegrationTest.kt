package com.aechak.api.security

import com.aechak.api.support.IntegrationTestBase
import com.aechak.domain.user.user.User
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.security.web.FilterChainProxy
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

/**
 * 토큰 없는 요청에 SNS 피드를 어디까지 열어 주는지와 토큰 없는 요청, 온보딩 전 토큰이 리포스트 경로에 닿지 못하는지 고정한다.
 * 깨지면 비로그인 사용자가 탐색 피드를 보지 못하거나 로그인하지 않았거나 온보딩을 마치지 않은 사용자가 리포스트를 남긴다.
 */
class GuestSnsFeedAccessIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var context: WebApplicationContext

    @Autowired
    private lateinit var securityFilterChain: FilterChainProxy

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc =
            MockMvcBuilders
                .webAppContextSetup(context)
                .addFilters<DefaultMockMvcBuilder>(securityFilterChain)
                .build()
    }

    @Test
    fun `토큰 없이 탐색 피드를 부르면 200이다`() {
        mockMvc
            .perform(get("/api/v1/sns/feeds/explore"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.items").isArray)
    }

    @Test
    fun `토큰 없이 리포스트하거나 리포스트를 취소하면 401 20004다`() {
        mockMvc
            .perform(post("/api/v1/sns/posts/$POST_PUBLIC_ID/repost"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.errorCode").value(20004))
        mockMvc
            .perform(delete("/api/v1/sns/posts/$POST_PUBLIC_ID/repost"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.errorCode").value(20004))
    }

    @Test
    fun `온보딩 전 사용자가 토큰을 붙여 리포스트하거나 취소하면 403 20006이다`() {
        val pendingId = tx.execute { User.preRegister().also { em.persist(it) }.id }!!
        val token = "Bearer ${mintAccessToken(pendingId)}"

        mockMvc
            .perform(post("/api/v1/sns/posts/$POST_PUBLIC_ID/repost").header(HttpHeaders.AUTHORIZATION, token))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.errorCode").value(20006))
        mockMvc
            .perform(delete("/api/v1/sns/posts/$POST_PUBLIC_ID/repost").header(HttpHeaders.AUTHORIZATION, token))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.errorCode").value(20006))
    }

    companion object {
        // 인증과 상태 게이트가 게시물 조회보다 먼저라 없는 게시물로 본다
        private const val POST_PUBLIC_ID = "01JF9W3Q6R8T2V4X7Z9B1C3D5J"
    }
}
