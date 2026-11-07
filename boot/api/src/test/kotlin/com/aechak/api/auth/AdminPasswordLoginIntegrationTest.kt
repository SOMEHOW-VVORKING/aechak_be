package com.aechak.api.auth

import com.aechak.api.auth.cookie.RefreshCookieFactory
import com.aechak.api.support.IntegrationTestBase
import com.aechak.application.auth.error.AuthErrorCode
import com.aechak.application.auth.port.PasswordHasher
import com.aechak.domain.user.credential.AdminCredential
import com.aechak.domain.user.user.User
import com.aechak.domain.user.user.enums.UserStatus
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

/**
 * 어드민 비밀번호 로그인 — 소셜 로그인과 병존하는 두 번째 진입 채널.
 *
 * 이 스펙이 지키는 핵심은 둘이다. refresh가 응답 body가 아니라 Set-Cookie로 나가는 것(그래야
 * 브라우저 세션이 새로고침을 견딘다), 그리고 아이디 부재와 비밀번호 불일치가 같은 응답으로
 * 거절되는 것(가르면 아이디 목록을 먼저 모을 수 있다).
 */
class AdminPasswordLoginIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var context: WebApplicationContext

    @Autowired
    private lateinit var passwordHasher: PasswordHasher

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUpMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build()
    }

    @Test
    fun `아이디와 비밀번호가 맞으면 access를 body로, refresh를 쿠키로 받는다`() {
        seedCredential(LOGIN_ID, PASSWORD)

        mockMvc
            .perform(loginRequest(LOGIN_ID, PASSWORD))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.accessToken").isNotEmpty)
            .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
            // refresh가 body에 없는 것이 계약이다 — JS가 만질 수 없어야 한다.
            .andExpect(jsonPath("$.data.refreshToken").doesNotExist())
            .andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.containsString(RefreshCookieFactory.REFRESH_COOKIE)))
            .andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.containsString("HttpOnly")))
    }

    @Test
    fun `비밀번호가 틀리면 20014로 거절하고 쿠키를 심지 않는다`() {
        seedCredential(LOGIN_ID, PASSWORD)

        mockMvc
            .perform(loginRequest(LOGIN_ID, "wrong-password-value"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.errorCode").value(AuthErrorCode.INVALID_CREDENTIALS.code))
            .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
    }

    @Test
    fun `없는 아이디는 비밀번호가 틀린 것과 같은 응답을 받는다`() {
        seedCredential(LOGIN_ID, PASSWORD)

        // 아이디가 없다는 사실이 응답으로 드러나면 공격자가 아이디 목록을 먼저 모을 수 있다.
        // 그래서 404도 아니고 다른 코드도 아니라 비밀번호 불일치와 똑같아야 한다.
        mockMvc
            .perform(loginRequest("no-such-admin", PASSWORD))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.errorCode").value(AuthErrorCode.INVALID_CREDENTIALS.code))
    }

    @ParameterizedTest
    @EnumSource(value = UserStatus::class, names = ["SUSPENDED", "BANNED", "WITHDRAWN"])
    fun `정지·영구정지·탈퇴 계정은 비밀번호가 맞아도 들어올 수 없다`(status: UserStatus) {
        val userId = seedCredential(LOGIN_ID, PASSWORD)
        tx.execute {
            em
                .createQuery(
                    "update User u set u.status = :s where u.id = :id",
                ).setParameter("s", status)
                .setParameter("id", userId)
                .executeUpdate()
        }

        mockMvc
            .perform(loginRequest(LOGIN_ID, PASSWORD))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.errorCode").value(AuthErrorCode.ACCOUNT_BLOCKED.code))
    }

    @Test
    fun `자격은 있고 역할이 GENERAL이면 로그인은 되고 어드민 권한은 붙지 않는다`() {
        seedCredential(LOGIN_ID, PASSWORD)

        // 로그인은 역할을 보지 않는다(소셜도 같다). 권한 판정은 어드민 모듈 게이트가 한다 —
        // 자격만 만들고 승격을 빠뜨리면 로그인 200인데 어드민 API가 전부 403이 되므로,
        // 계정 발급 절차에서 승격을 함께 해야 한다는 것을 이 스펙이 못박는다.
        mockMvc
            .perform(loginRequest(LOGIN_ID, PASSWORD))
            .andExpect(status().isOk)

        val role = tx.execute { em.find(User::class.java, userIdOf(LOGIN_ID)).role }
        org.junit.jupiter.api.Assertions
            .assertEquals(com.aechak.domain.user.user.enums.UserRole.GENERAL, role)
    }

    @Test
    fun `아이디나 비밀번호가 비어 있으면 요청 단계에서 거절한다`() {
        mockMvc
            .perform(loginRequest("", PASSWORD))
            .andExpect(status().isBadRequest)
    }

    private fun loginRequest(
        loginId: String,
        password: String,
    ) = post("/api/v1/auth/login/password")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""{"loginId":"$loginId","password":"$password"}""")

    /** 온보딩까지 마친 ACTIVE 계정에 비밀번호 자격을 붙인다. 승격은 하지 않는다. */
    private fun seedCredential(
        loginId: String,
        password: String,
    ): Long =
        tx.execute {
            val user = User.preRegister()
            em.persist(user)
            // 닉네임은 한글·영문·숫자 2~12자만 받는다. loginId 의 하이픈이 그 규칙에 걸린다.
            user.completeOnboarding("운영자테스트")
            em.persist(AdminCredential.issue(user, loginId, passwordHasher.hash(password)))
            em.flush()
            user.id
        }!!

    private fun userIdOf(loginId: String): Long =
        tx.execute {
            em
                .createQuery("select c.user.id from AdminCredential c where c.loginId = :id", Long::class.javaObjectType)
                .setParameter("id", loginId)
                .singleResult
        }!!

    companion object {
        private const val LOGIN_ID = "admin-test"
        private const val PASSWORD = "test-password-9f3k-2026"
    }
}
