package com.aechak.api.user.user

import com.aechak.api.auth.cookie.RefreshCookieFactory
import com.aechak.api.support.FakeFileStorage
import com.aechak.api.support.IntegrationTestBase
import com.aechak.application.auth.error.AuthErrorCode
import com.aechak.application.auth.service.RejoinPolicy
import com.aechak.application.file.port.FileStorage
import com.aechak.domain.user.user.User
import com.aechak.domain.user.user.enums.UserStatus
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.web.FilterChainProxy
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.RequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 회원 탈퇴 요청을 보안 필터, MySQL, Redis까지 포함해 검증한다.
 * 세션과 프로필 이미지는 DB 커밋 후 정리되므로 tx.execute로 실제 트랜잭션을 커밋한다.
 * 모든 refresh 세션이 삭제되는지는 Redis 키를 직접 조회해 확인한다.
 */
class WithdrawalIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var context: WebApplicationContext

    @Autowired
    private lateinit var securityFilterChain: FilterChainProxy

    @Autowired
    private lateinit var redis: StringRedisTemplate

    @Autowired
    private lateinit var fileStorage: FileStorage

    @Autowired
    private lateinit var rejoinPolicy: RejoinPolicy

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
    fun `탈퇴하면 204와 함께 refresh 쿠키 만료 헤더가 내려간다`() {
        val userId = createOnboardedUser("코코집사")

        mockMvc
            .perform(withdrawRequest(userId))
            .andExpect(status().isNoContent)
            .andExpect(header().string(HttpHeaders.SET_COOKIE, containsCookieExpiry()))
    }

    @Test
    fun `탈퇴하면 상태가 WITHDRAWN으로 바뀌고 프로필 행이 사라진다`() {
        val userId = createOnboardedUser("코코집사")

        mockMvc.perform(withdrawRequest(userId)).andExpect(status().isNoContent)

        assertEquals(UserStatus.WITHDRAWN, statusOf(userId))
        assertEquals(0, countProfiles(userId))
    }

    @Test
    fun `탈퇴하면 Redis의 전 세션이 실제로 지워진다`() {
        val providerId = "kakao-session"
        val userId = signUpAndOnboard(providerId, "세션집사")
        mockMvc.perform(loginRequest(providerId)).andExpect(status().isOk) // 두 번째 기기 세션
        assertTrue(sessionKeys(userId).size >= 2, "탈퇴 전에 세션이 남아 있어야 검증이 의미 있다")

        mockMvc.perform(withdrawRequest(userId)).andExpect(status().isNoContent)

        assertTrue(sessionKeys(userId).isEmpty(), "탈퇴 후 refresh 세션 키가 남아 있다")
    }

    @Test
    fun `탈퇴하면 소셜 이메일이 삭제된다`() {
        val providerId = "kakao-pii"
        val userId = signUpAndOnboard(providerId, "삭제집사")
        assertEquals("owner@example.com", socialEmailOf(providerId))

        mockMvc.perform(withdrawRequest(userId)).andExpect(status().isNoContent)

        assertNull(socialEmailOf(providerId), "탈퇴 후 소셜 이메일이 남아 있다")
    }

    @Test
    fun `탈퇴하면 프로필 이미지가 스토리지에서도 삭제된다`() {
        val imageKey = "users/profile/withdrawal-target.webp"
        val userId = createOnboardedUser("사진집사", imageKey)

        mockMvc.perform(withdrawRequest(userId)).andExpect(status().isNoContent)

        // 프로필 행뿐 아니라 공개 버킷의 이미지도 삭제되어야 한다.
        assertTrue(imageKey in (fileStorage as FakeFileStorage).deletedKeys, "프로필 이미지가 스토리지에 남아 있다")
    }

    @Test
    fun `탈퇴한 계정이 쓰던 닉네임을 다른 유저가 다시 쓸 수 있다`() {
        val userId = createOnboardedUser("코코집사")
        mockMvc.perform(withdrawRequest(userId)).andExpect(status().isNoContent)

        // 탈퇴한 사용자의 닉네임을 삭제해야 다른 사용자가 같은 닉네임을 저장할 수 있다.
        val other = createOnboardedUser("코코집사")

        assertNotEquals(userId, other)
    }

    @Test
    fun `탈퇴 후에는 같은 액세스 토큰으로 API를 쓸 수 없다`() {
        val userId = createOnboardedUser("코코집사")
        val token = mintAccessToken(userId)
        mockMvc.perform(withdrawRequest(userId)).andExpect(status().isNoContent)

        mockMvc
            .perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.errorCode").value(AuthErrorCode.ACCOUNT_BLOCKED.code))
    }

    @Test
    fun `탈퇴 요청을 두 번 보내면 두 번째는 상태 필터가 막는다`() {
        val userId = createOnboardedUser("코코집사")
        mockMvc.perform(withdrawRequest(userId)).andExpect(status().isNoContent)

        // 두 번째 요청은 컨트롤러에 도달하기 전에 UserStatusFilter에서 차단된다.
        mockMvc
            .perform(withdrawRequest(userId))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.errorCode").value(AuthErrorCode.ACCOUNT_BLOCKED.code))
    }

    @Test
    fun `온보딩 미완료 계정도 탈퇴할 수 있고 탈퇴 가능 여부 조회까지 열려 있다`() {
        val providerId = "kakao-pending"
        mockMvc.perform(loginRequest(providerId)).andExpect(status().isOk)
        val userId = userIdLinkedTo(providerId)

        // 온보딩을 완료하지 않은 사용자도 앱에서 계정을 삭제할 수 있어야 한다.
        mockMvc.perform(withdrawalCheckRequest(userId)).andExpect(status().isOk)
        mockMvc.perform(withdrawRequest(userId)).andExpect(status().isNoContent)

        assertEquals(UserStatus.WITHDRAWN, statusOf(userId))
    }

    @Test
    fun `탈퇴 가능 여부 조회는 withdrawable true를 내려준다`() {
        val userId = createOnboardedUser("코코집사")

        // 아직 탈퇴 제한 사유를 제공하는 도메인이 없으므로 모든 사용자가 조회를 통과한다.
        mockMvc
            .perform(withdrawalCheckRequest(userId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.withdrawable").value(true))
    }

    @Test
    fun `재가입 제한 기간 중에 같은 소셜 계정으로 다시 로그인하면 거부된다`() {
        val providerId = "kakao-in-grace"
        withdraw(signUpAndOnboard(providerId, "탈퇴할집사"))

        mockMvc
            .perform(loginRequest(providerId))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.errorCode").value(AuthErrorCode.REJOIN_BLOCKED.code))
    }

    @Test
    fun `재가입 제한 기간이 지나면 새 계정으로 재가입되고 옛 계정은 탈퇴 상태로 남는다`() {
        val providerId = "kakao-after-grace"
        val oldUserId = signUpAndOnboard(providerId, "보리집사")
        withdraw(oldUserId)
        backdateWithdrawalPastRejoinBlock(oldUserId)

        mockMvc
            .perform(loginRequest(providerId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.user.isNew").value(true))
            .andExpect(jsonPath("$.data.user.status").value(UserStatus.PENDING_ONBOARDING.name))

        val newUserId = userIdLinkedTo(providerId)
        assertNotEquals(oldUserId, newUserId) // 옛 계정을 되살리지 않고 새 계정으로 시작한다
        assertEquals(UserStatus.WITHDRAWN, statusOf(oldUserId))
        assertEquals(UserStatus.PENDING_ONBOARDING, statusOf(newUserId))
    }

    @Test
    fun `재가입한 계정도 다시 탈퇴할 수 있다`() {
        val providerId = "kakao-twice"
        val oldUserId = signUpAndOnboard(providerId, "두번집사")
        withdraw(oldUserId)
        backdateWithdrawalPastRejoinBlock(oldUserId)
        mockMvc.perform(loginRequest(providerId)).andExpect(status().isOk)
        val newUserId = userIdLinkedTo(providerId)

        withdraw(newUserId)

        assertEquals(UserStatus.WITHDRAWN, statusOf(oldUserId))
        assertEquals(UserStatus.WITHDRAWN, statusOf(newUserId))
    }

    /** 소셜 연결 없이 ACTIVE 상태의 테스트 사용자를 생성한다. */
    private fun createOnboardedUser(
        nickname: String,
        profileImageKey: String? = null,
    ): Long =
        tx.execute {
            val user = User.preRegister()
            em.persist(user)
            user.completeOnboarding(nickname)
            profileImageKey?.let { user.updateProfile(nickname, null, it) }
            em.flush()
            user.id
        }!!

    /** 소셜 로그인과 온보딩을 마친 테스트 사용자를 생성한다. */
    private fun signUpAndOnboard(
        providerId: String,
        nickname: String,
    ): Long {
        mockMvc.perform(loginRequest(providerId)).andExpect(status().isOk)
        val userId = userIdLinkedTo(providerId)
        tx.execute { em.find(User::class.java, userId).completeOnboarding(nickname) }
        return userId
    }

    private fun withdraw(userId: Long) = mockMvc.perform(withdrawRequest(userId)).andExpect(status().isNoContent)

    private fun withdrawRequest(userId: Long): RequestBuilder =
        delete("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer ${mintAccessToken(userId)}")

    private fun withdrawalCheckRequest(userId: Long): RequestBuilder =
        get("/api/v1/users/me/withdrawal/check")
            .header(HttpHeaders.AUTHORIZATION, "Bearer ${mintAccessToken(userId)}")

    /** 테스트용 검증기가 읽을 수 있는 "providerId:email" 형식으로 로그인한다. */
    private fun loginRequest(providerId: String): RequestBuilder =
        post("/api/v1/auth/login/kakao")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"idToken":"$providerId:owner@example.com"}""")

    /** 운영 어댑터와 같은 키 형식으로 Redis의 refresh 세션을 조회한다. */
    private fun sessionKeys(userId: Long): Set<String> = redis.keys("refresh:$userId:*")

    private fun statusOf(userId: Long): UserStatus = tx.execute { em.find(User::class.java, userId).status }!!

    private fun socialEmailOf(providerId: String): String? =
        tx.execute {
            em
                .createQuery("select i.email from SocialIdentity i where i.providerId = :param", String::class.java)
                .setParameter("param", providerId)
                .resultList
                .firstOrNull()
        }

    private fun userIdLinkedTo(providerId: String): Long =
        singleLong("select i.user.id from SocialIdentity i where i.providerId = :param", providerId)

    private fun countProfiles(userId: Long): Long = singleLong("select count(p) from UserProfile p where p.userId = :param", userId)

    private fun singleLong(
        jpql: String,
        param: Any,
    ): Long =
        tx.execute {
            em
                .createQuery(jpql, Long::class.javaObjectType)
                .setParameter("param", param)
                .singleResult
        }!!

    /**
     * 재가입 제한 기간이 지난 상태를 만들기 위해 withdrawnAt을 직접 변경한다.
     * 기간은 설정(RejoinPolicy)을 그대로 써서 yml만 바꿔도 테스트가 어긋나지 않게 한다.
     */
    private fun backdateWithdrawalPastRejoinBlock(userId: Long) {
        val withdrawnAt = LocalDateTime.now().minus(rejoinPolicy.blockedPeriod).minusDays(1)
        tx.execute {
            em
                .createQuery("update User u set u.withdrawnAt = :at where u.id = :id")
                .setParameter("at", withdrawnAt)
                .setParameter("id", userId)
                .executeUpdate()
        }
    }

    /** 같은 이름과 Path의 쿠키가 Max-Age=0으로 내려오는지 확인한다. */
    private fun containsCookieExpiry() =
        allOf(
            containsString("${RefreshCookieFactory.REFRESH_COOKIE}="),
            containsString("Max-Age=0"),
        )
}
