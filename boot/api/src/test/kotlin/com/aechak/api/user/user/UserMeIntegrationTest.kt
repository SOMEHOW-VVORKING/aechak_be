package com.aechak.api.user.user

import com.aechak.api.support.IntegrationTestBase
import com.aechak.application.file.error.FileErrorCode
import com.aechak.common.error.CommonErrorCode
import com.aechak.domain.user.user.User
import org.assertj.core.api.Assertions.assertThat
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 통합 — 내 정보(/users/me) 계열이 실 보안 체인·실 MySQL에서 계약대로 동작하는지.
 *
 * "프로필 행 없는 ACTIVE 유저"는 정상 플로우(온보딩 완료 전이)로는 나올 수 없는 불변식 위반 데이터다
 * (status만 직접 UPDATE한 계정 — createActiveUser 픽스처·dev DB 수동 활성화). 코드가 아니라 데이터를
 * 고치는 게 답이므로, 우회(null 강하·자가 치유) 없이 500으로 드러나는 동작을 회귀 대상으로 고정한다.
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
    fun `프로필 행 없는 ACTIVE 유저의 내 정보 조회는 500으로 드러난다`() {
        val userId = createActiveUser() // status만 강제 전이 — user_profiles 행 없음
        val token = mintAccessToken(userId)

        mockMvc
            .perform(getMe(token))
            .andExpect(status().isInternalServerError)
            .andExpect(jsonPath("$.errorCode").value(CommonErrorCode.INTERNAL_SERVER_ERROR.code))
    }

    @Test
    fun `프로필 행 없는 ACTIVE 유저의 프로필 수정도 500으로 드러난다`() {
        val userId = createActiveUser()
        val token = mintAccessToken(userId)

        mockMvc
            .perform(putProfile(token, """{"nickname":"코코집사","bio":null,"profileImageKey":null}"""))
            .andExpect(status().isInternalServerError)
            .andExpect(jsonPath("$.errorCode").value(CommonErrorCode.INTERNAL_SERVER_ERROR.code))
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

    /**
     * 운영자 초기화가 users 행을 잠근 동안 회원이 바꾸지 않은 이미지 key로 소개글만 고치면, 회원 쪽은 잠그기 전에
     * 옛 key를 읽는다. 잠근 뒤 저장값과 다시 대조하지 않으면 운영자가 막 비운 key가 되살아난다.
     */
    @Test
    fun `운영자 초기화와 겹친 프로필 수정은 초기화 전 이미지 key를 되살리지 않는다`() {
        val userId = createOnboardedUser("보리집사")
        val oldKey = "users/$userId/profile/old.png"
        tx.executeWithoutResult {
            em
                .createQuery("update UserProfile p set p.profileImageKey = :key, p.bio = :bio where p.userId = :id")
                .setParameter("key", oldKey)
                .setParameter("bio", "괴롭힘 문구")
                .setParameter("id", userId)
                .executeUpdate()
        }
        val token = mintAccessToken(userId)
        val locked = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        val operatorReset =
            executor.submit {
                tx.executeWithoutResult {
                    em
                        .createNativeQuery("select id from users where id = :id for update")
                        .setParameter("id", userId)
                        .singleResult
                    em
                        .createQuery("update UserProfile p set p.profileImageKey = null, p.bio = null where p.userId = :id")
                        .setParameter("id", userId)
                        .executeUpdate()
                    locked.countDown()
                    // 회원 요청이 잠그기 전 읽기를 마치고 행 잠금을 기다리게 둔다
                    Thread.sleep(1_000)
                }
            }
        assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue()

        mockMvc
            .perform(putProfile(token, """{"nickname":"보리집사","bio":"새 소개","profileImageKey":"$oldKey"}"""))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.errorCode").value(FileErrorCode.FILE_ACCESS_DENIED.code))

        operatorReset.get(5, TimeUnit.SECONDS)
        executor.shutdown()
        val stored =
            tx.execute {
                em
                    .createQuery("select p.profileImageKey from UserProfile p where p.userId = :id", String::class.java)
                    .setParameter("id", userId)
                    .resultList
                    .single()
            }
        assertThat(stored).isNull()
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
