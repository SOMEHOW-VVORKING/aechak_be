package com.aechak.api.security

import com.aechak.api.support.IntegrationTestBase
import com.aechak.application.auth.error.AuthErrorCode
import com.aechak.common.error.CommonErrorCode
import com.aechak.domain.user.user.User
import com.aechak.domain.user.user.enums.UserStatus
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.web.FilterChainProxy
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.time.LocalDateTime

/**
 * 제재가 실제로 구매자 API를 막는지 고정한다 — 어드민 화면만 만들고 여기가 비면 제재가 아무 효력이 없다.
 * 조립된 SecurityConfig의 제한 경로 목록까지 그대로 타므로 정책 변경이 이 테스트에 잡힌다.
 */
class SanctionedAccountGateIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var context: WebApplicationContext

    @Autowired
    private lateinit var securityFilterChain: FilterChainProxy

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUpMockMvc() {
        mockMvc =
            MockMvcBuilders
                .webAppContextSetup(context)
                .addFilters<org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder>(securityFilterChain)
                .build()
    }

    @Test
    fun `임시 정지 계정은 조회조차 403 20005로 막힌다`() {
        val userId = createMember(UserStatus.SUSPENDED, LocalDateTime.now().plusDays(7))

        mockMvc
            .perform(bearer(get("/api/v1/users/me"), mintAccessToken(userId)))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.errorCode").value(AuthErrorCode.ACCOUNT_BLOCKED.code))
    }

    @Test
    fun `영구 정지 계정도 403 20005로 막힌다`() {
        val userId = createMember(UserStatus.BANNED, null)

        mockMvc
            .perform(bearer(get("/api/v1/users/me"), mintAccessToken(userId)))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.errorCode").value(AuthErrorCode.ACCOUNT_BLOCKED.code))
    }

    @Test
    fun `기간이 지난 임시 정지는 다시 통과하고 유효 상태를 ACTIVE로 내린다`() {
        val userId = createMember(UserStatus.SUSPENDED, LocalDateTime.now().minusMinutes(1))

        mockMvc
            .perform(bearer(get("/api/v1/users/me"), mintAccessToken(userId)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.status").value(UserStatus.ACTIVE.name))
            .andExpect(jsonPath("$.data.nickname").value(nicknameOf(userId)))
    }

    @Test
    fun `기간이 지난 임시 정지는 프로필 수정도 다시 된다`() {
        val userId = createMember(UserStatus.SUSPENDED, LocalDateTime.now().minusMinutes(1))

        mockMvc
            .perform(
                bearer(put("/api/v1/users/me/profile"), mintAccessToken(userId))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"nickname":"풀린회원","bio":null,"profileImageKey":null}"""),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.nickname").value("풀린회원"))
    }

    @Test
    fun `이용 제한 계정은 조회에서 프로필을 그대로 보고 리뷰 작성만 403 20013으로 막힌다`() {
        val userId = createMember(UserStatus.RESTRICTED, null)
        val token = mintAccessToken(userId)

        mockMvc
            .perform(bearer(get("/api/v1/users/me"), token))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.status").value(UserStatus.RESTRICTED.name))
            .andExpect(jsonPath("$.data.nickname").value(nicknameOf(userId)))

        mockMvc
            .perform(
                bearer(post("/api/v1/reviews"), token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"),
            ).andExpect(status().isForbidden)
            .andExpect(jsonPath("$.errorCode").value(AuthErrorCode.ACCOUNT_RESTRICTED.code))
    }

    /** 이용 제한은 커뮤니티성 쓰기만 막는 제재다 — 자기 프로필 수정이 500으로 터지면 안 된다. */
    @Test
    fun `이용 제한 계정의 프로필 수정은 통과한다`() {
        val userId = createMember(UserStatus.RESTRICTED, null)

        mockMvc
            .perform(
                bearer(put("/api/v1/users/me/profile"), mintAccessToken(userId))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"nickname":"제한회원","bio":null,"profileImageKey":null}"""),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.nickname").value("제한회원"))
    }

    @Test
    fun `이용 제한 계정의 주문은 막지 않는다 — 거래를 끊는 제재는 정지다`() {
        val userId = createMember(UserStatus.RESTRICTED, null)

        // 본문이 비어 요청 자체는 실패하지만, 게이트가 아니라 컨트롤러의 입력 검증까지 도달했음을 본다
        mockMvc
            .perform(
                bearer(post("/api/v1/order-groups"), mintAccessToken(userId))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(CommonErrorCode.INVALID_REQUEST.code))
    }

    @Test
    fun `정상 계정은 리뷰 작성 경로도 게이트를 통과한다`() {
        val token = mintAccessToken(createActiveUser())

        mockMvc
            .perform(
                bearer(post("/api/v1/reviews"), token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(CommonErrorCode.INVALID_REQUEST.code))
    }

    /**
     * 상품 문의 등록은 상품 하위 경로(`/products/{id}/inquiries`)라 제한 경로를 접두로만 적으면 빠진다.
     * 빠져 있던 자리라서 경로를 문자열로 못박아 둔다 — 컨트롤러 매핑이 바뀌면 여기서 걸린다.
     */
    @Test
    fun `이용 제한 계정의 상품 문의 등록은 403 20013으로 막힌다`() {
        val userId = createMember(UserStatus.RESTRICTED, null)

        mockMvc
            .perform(
                bearer(post("/api/v1/products/01J0000000000000000000PROD/inquiries"), mintAccessToken(userId))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"),
            ).andExpect(status().isForbidden)
            .andExpect(jsonPath("$.errorCode").value(AuthErrorCode.ACCOUNT_RESTRICTED.code))
    }

    @Test
    fun `이용 제한 계정의 상품 문의 철회도 403 20013으로 막힌다`() {
        val userId = createMember(UserStatus.RESTRICTED, null)

        mockMvc
            .perform(bearer(delete("/api/v1/product-inquiries/01J0000000000000000000INQ1"), mintAccessToken(userId)))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.errorCode").value(AuthErrorCode.ACCOUNT_RESTRICTED.code))
    }

    /** 과차단 방지 — 패턴이 상품 하위의 다른 쓰기나 정상 계정까지 삼키면 안 된다. */
    @Test
    fun `정상 계정은 상품 문의 등록 경로에서 게이트를 통과한다`() {
        val token = mintAccessToken(createActiveUser())

        mockMvc
            .perform(
                bearer(post("/api/v1/products/01J0000000000000000000PROD/inquiries"), token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(CommonErrorCode.INVALID_REQUEST.code))
    }

    /** 제한은 쓰기만 막는다 — 제한 계정도 상품 문의 목록은 읽어야 한다. */
    @Test
    fun `이용 제한 계정의 상품 문의 조회는 게이트를 통과한다`() {
        val userId = createMember(UserStatus.RESTRICTED, null)

        mockMvc
            .perform(bearer(get("/api/v1/users/me/product-inquiries"), mintAccessToken(userId)))
            .andExpect(status().isOk)
    }

    private fun createMember(
        status: UserStatus,
        sanctionUntil: LocalDateTime?,
    ): Long =
        tx.execute {
            val user = User.preRegister()
            em.persist(user)
            user.completeOnboarding("회원${System.nanoTime()}".take(12))
            em.flush()
            em
                .createQuery("update User u set u.status = :st, u.sanctionUntil = :until where u.id = :id")
                .setParameter("st", status)
                .setParameter("until", sanctionUntil)
                .setParameter("id", user.id)
                .executeUpdate()
            user.id
        }!!

    private fun nicknameOf(userId: Long): String =
        tx.execute {
            em
                .createQuery("select p.nickname from UserProfile p where p.userId = :id", String::class.java)
                .setParameter("id", userId)
                .singleResult
        }!!

    private fun bearer(
        builder: MockHttpServletRequestBuilder,
        token: String,
    ): MockHttpServletRequestBuilder = builder.header(HttpHeaders.AUTHORIZATION, "Bearer $token")
}
