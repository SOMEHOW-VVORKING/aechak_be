package com.aechak.api.user.user

import com.aechak.api.support.IntegrationTestBase
import com.aechak.domain.user.user.User
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.web.FilterChainProxy
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

/**
 * 통합 — 내 정보(/users/me) 계열이 실 보안 체인·실 MySQL에서 계약대로 동작하는지.
 *
 * 핵심 회귀 대상: "프로필 행 없는 ACTIVE 유저". 정상 플로우(온보딩 완료 전이)로는 나오지 않지만,
 * status만 직접 UPDATE해 활성화한 계정(createActiveUser 픽스처·dev DB 수동 활성화 계정)이 실존하고,
 * 이 상태에서 User.profile 접근이 IllegalStateException → 500으로 터졌다. 조회는 null 강하,
 * 프로필 수정은 생성(자가 치유)으로 동작해야 한다.
 */
class UserMeIntegrationTest : IntegrationTestBase() {
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
    fun `프로필 행 없는 ACTIVE 유저의 내 정보 조회는 500이 아니라 프로필 null로 응답한다`() {
        val userId = createActiveUser() // status만 강제 전이 — user_profiles 행 없음
        val token = mintAccessToken(userId)

        mockMvc
            .perform(getMe(token))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.status").value("ACTIVE"))
            .andExpect(jsonPath("$.data.nickname").isEmpty)
            .andExpect(jsonPath("$.data.isPhoneVerified").value(false))
    }

    @Test
    fun `프로필 행 없는 ACTIVE 유저의 프로필 수정은 프로필을 생성하고 이후 조회에 반영된다`() {
        val userId = createActiveUser()
        val token = mintAccessToken(userId)

        mockMvc
            .perform(putProfile(token, """{"nickname":"코코집사","bio":"자가 치유 확인","profileImageKey":null}"""))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.nickname").value("코코집사"))

        mockMvc
            .perform(getMe(token))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.nickname").value("코코집사"))
            .andExpect(jsonPath("$.data.bio").value("자가 치유 확인"))
    }

    @Test
    fun `온보딩 완료 전이로 프로필이 생긴 유저의 내 정보 조회는 닉네임을 내려준다`() {
        val userId = createOnboardedUser("보리집사")
        val token = mintAccessToken(userId)

        mockMvc
            .perform(getMe(token))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.status").value("ACTIVE"))
            .andExpect(jsonPath("$.data.nickname").value("보리집사"))
    }

    /** 정상 경로 미러 — 도메인 전이(completeOnboarding)로 프로필까지 생성된 ACTIVE 유저. */
    private fun createOnboardedUser(nickname: String): Long =
        tx.execute {
            val user = User.preRegister()
            em.persist(user)
            user.completeOnboarding(nickname)
            em.flush()
            user.id
        }!!

    private fun getMe(token: String) = get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer $token")

    private fun putProfile(
        token: String,
        body: String,
    ) = put("/api/v1/users/me/profile")
        .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
        .contentType(MediaType.APPLICATION_JSON)
        .content(body)
}
