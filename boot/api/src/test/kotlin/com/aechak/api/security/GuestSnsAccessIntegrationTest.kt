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
 * 토큰 없는 요청에 SNS를 어디까지 열어 주는지 고정한다.
 * 다른 사용자 프로필만 열고 내 프로필과 프로필 아래 경로는 로그인을 요구한다.
 */
class GuestSnsAccessIntegrationTest : IntegrationTestBase() {
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
    fun `토큰 없이 프로필을 조회하면 200이다`() {
        val publicId = onboardedUserPublicId()

        mockMvc
            .perform(get("/api/v1/sns/users/$publicId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.publicId").value(publicId))
    }

    @Test
    fun `같은 프로필 경로에 토큰을 붙이면 관계 필드가 보는 사람 기준으로 채워진다`() {
        val publicId = onboardedUserPublicId()
        val viewerId = createActiveUser()
        mockMvc
            .perform(
                post("/api/v1/sns/users/$publicId/mute")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer ${mintAccessToken(viewerId)}"),
            ).andExpect(status().isCreated)

        mockMvc
            .perform(
                get("/api/v1/sns/users/$publicId")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer ${mintAccessToken(viewerId)}"),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.mutedByMe").value(true))

        mockMvc
            .perform(get("/api/v1/sns/users/$publicId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.mutedByMe").value(false))
    }

    @Test
    fun `토큰 없이 내 프로필을 부르면 401 20004다`() {
        mockMvc
            .perform(get("/api/v1/sns/me"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.errorCode").value(20004))
    }

    @Test
    fun `프로필 아래 경로는 토큰 없이 열리지 않는다`() {
        val publicId = onboardedUserPublicId()

        mockMvc
            .perform(get("/api/v1/sns/users/$publicId/followers"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `토큰 없이 차단하거나 차단 해제하면 401 20004다`() {
        val publicId = onboardedUserPublicId()

        mockMvc
            .perform(post("/api/v1/sns/users/$publicId/block"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.errorCode").value(20004))
        mockMvc
            .perform(delete("/api/v1/sns/users/$publicId/block"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.errorCode").value(20004))
    }

    @Test
    fun `토큰 없이 뮤트하거나 뮤트 해제하면 401 20004다`() {
        val publicId = onboardedUserPublicId()

        mockMvc
            .perform(post("/api/v1/sns/users/$publicId/mute"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.errorCode").value(20004))
        mockMvc
            .perform(delete("/api/v1/sns/users/$publicId/mute"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.errorCode").value(20004))
    }

    @Test
    fun `온보딩 전 사용자는 토큰을 붙이면 403 20006이고 토큰 없이는 200이다`() {
        val publicId = onboardedUserPublicId()
        val pendingId = tx.execute { User.preRegister().also { em.persist(it) }.id }!!

        mockMvc
            .perform(
                get("/api/v1/sns/users/$publicId")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer ${mintAccessToken(pendingId)}"),
            ).andExpect(status().isForbidden)
            .andExpect(jsonPath("$.errorCode").value(20006))

        mockMvc
            .perform(get("/api/v1/sns/users/$publicId"))
            .andExpect(status().isOk)
    }

    private fun onboardedUserPublicId(): String =
        tx.execute {
            val user = User.preRegister()
            em.persist(user)
            user.completeOnboarding("guestcheck")
            em.flush()
            user.publicId
        }!!
}
