package com.aechak.api.sns

import com.aechak.api.support.IntegrationTestBase
import com.aechak.domain.sns.stats.repository.SnsUserStatsRepository
import com.aechak.domain.user.pet.Breed
import com.aechak.domain.user.pet.PetProfile
import com.aechak.domain.user.pet.enums.Species
import com.aechak.domain.user.user.User
import com.aechak.domain.user.user.enums.UserStatus
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.security.web.FilterChainProxy
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

/**
 * 통합: SNS 프로필 조회가 실 보안 필터체인과 MySQL을 지나 계약대로 응답하는지.
 * 깨지면 볼 수 없는 사용자가 구분되어 드러나거나 내부 id가 응답에 실린다.
 */
class SnsProfileIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var context: WebApplicationContext

    @Autowired
    private lateinit var securityFilterChain: FilterChainProxy

    @Autowired
    private lateinit var statsRepository: SnsUserStatsRepository

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
    fun `비로그인도 프로필을 조회하고 관계 필드는 모두 false다`() {
        val target = onboardedUser("cocoowner", bio = "hello", imageKey = "users/profile/abc.webp")

        mockMvc
            .perform(get("/api/v1/sns/users/${target.publicId}"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.publicId").value(target.publicId))
            .andExpect(jsonPath("$.data.nickname").value("cocoowner"))
            .andExpect(jsonPath("$.data.profileImageUrl").value("https://fake-cdn.local/users/profile/abc.webp"))
            .andExpect(jsonPath("$.data.bio").value("hello"))
            .andExpect(jsonPath("$.data.pets").isArray)
            .andExpect(jsonPath("$.data.pets.length()").value(0))
            .andExpect(jsonPath("$.data.postCount").value(0))
            .andExpect(jsonPath("$.data.followerCount").value(0))
            .andExpect(jsonPath("$.data.followingCount").value(0))
            .andExpect(jsonPath("$.data.isMe").value(false))
            .andExpect(jsonPath("$.data.following").value(false))
            .andExpect(jsonPath("$.data.blockedByMe").value(false))
            .andExpect(jsonPath("$.data.mutedByMe").value(false))
    }

    @Test
    fun `세 수는 통계 행에서 읽는다`() {
        val target = onboardedUser("countowner")
        tx.execute {
            statsRepository.increaseFollowerCount(target.id)
            statsRepository.increaseFollowerCount(target.id)
            statsRepository.increaseFollowingCount(target.id)
        }

        mockMvc
            .perform(get("/api/v1/sns/users/${target.publicId}"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.followerCount").value(2))
            .andExpect(jsonPath("$.data.followingCount").value(1))
            .andExpect(jsonPath("$.data.postCount").value(0))
    }

    @Test
    fun `없는 publicId는 404 130000이다`() {
        expectUnavailable(mockMvc.perform(get("/api/v1/sns/users/01JZZZZZZZZZZZZZZZZZZZZZZZ")))
    }

    @Test
    fun `형식에 맞지 않는 publicId도 404 130000이다`() {
        expectUnavailable(mockMvc.perform(get("/api/v1/sns/users/not-a-ulid")))
    }

    @Test
    fun `탈퇴한 사용자는 404 130000이다`() {
        val target = onboardedUser("leaver")
        tx.execute { em.find(User::class.java, target.id).withdraw() }

        expectUnavailable(mockMvc.perform(get("/api/v1/sns/users/${target.publicId}")))
    }

    @Test
    fun `온보딩 전 사용자는 404 130000이다`() {
        val pending =
            tx.execute {
                User.preRegister().also { em.persist(it) }
            }!!

        expectUnavailable(mockMvc.perform(get("/api/v1/sns/users/${pending.publicId}")))
    }

    @Test
    fun `정지 사용자는 평소처럼 조회된다`() {
        val target = onboardedUser("suspended")
        tx.execute {
            em
                .createQuery("update User u set u.status = :st where u.id = :id")
                .setParameter("st", UserStatus.SUSPENDED)
                .setParameter("id", target.id)
                .executeUpdate()
        }

        mockMvc
            .perform(get("/api/v1/sns/users/${target.publicId}"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.nickname").value("suspended"))
    }

    @Test
    fun `내 프로필은 세 수 0과 publicId를 주고 isMe가 true다`() {
        val me = onboardedUser("myself")

        mockMvc
            .perform(get("/api/v1/sns/me").header(HttpHeaders.AUTHORIZATION, "Bearer ${mintAccessToken(me.id)}"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.publicId").value(me.publicId))
            .andExpect(jsonPath("$.data.nickname").value("myself"))
            .andExpect(jsonPath("$.data.postCount").value(0))
            .andExpect(jsonPath("$.data.followerCount").value(0))
            .andExpect(jsonPath("$.data.followingCount").value(0))
            .andExpect(jsonPath("$.data.isMe").value(true))
            .andExpect(jsonPath("$.data.following").value(false))
            .andExpect(jsonPath("$.data.blockedByMe").value(false))
            .andExpect(jsonPath("$.data.mutedByMe").value(false))
    }

    @Test
    fun `자기 publicId로 조회하면 isMe가 true이고 관계 필드는 모두 false다`() {
        val me = onboardedUser("selfview")

        mockMvc
            .perform(
                get("/api/v1/sns/users/${me.publicId}")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer ${mintAccessToken(me.id)}"),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.isMe").value(true))
            .andExpect(jsonPath("$.data.following").value(false))
            .andExpect(jsonPath("$.data.blockedByMe").value(false))
            .andExpect(jsonPath("$.data.mutedByMe").value(false))
    }

    @Test
    fun `다른 사용자가 로그인해 조회하면 isMe가 false다`() {
        val target = onboardedUser("otherone")
        val viewer = onboardedUser("viewerone")

        mockMvc
            .perform(
                get("/api/v1/sns/users/${target.publicId}")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer ${mintAccessToken(viewer.id)}"),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.isMe").value(false))
    }

    @Test
    fun `펫은 대표 펫부터 나오고 생년월 없는 펫은 품종만 갖는다`() {
        val target = onboardedUser("petowner")
        tx.execute {
            val user = em.find(User::class.java, target.id)
            val maltese = Breed.of(Species.DOG, "Maltese").also { em.persist(it) }
            val poodle = Breed.of(Species.DOG, "Poodle").also { em.persist(it) }
            em.persist(PetProfile.register(user, maltese, "first", birthYearMonth = null))
            em.persist(PetProfile.register(user, poodle, "second", birthYearMonth = "2021-03", isDefault = true))
        }

        mockMvc
            .perform(get("/api/v1/sns/users/${target.publicId}"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.pets.length()").value(2))
            .andExpect(jsonPath("$.data.pets[0].breedLabel").value("Poodle"))
            .andExpect(jsonPath("$.data.pets[0].birthYearMonth").value("2021-03"))
            .andExpect(jsonPath("$.data.pets[1].breedLabel").value("Maltese"))
            .andExpect(jsonPath("$.data.pets[1].birthYearMonth").value(nullValue()))
    }

    @Test
    fun `응답에 내부 id와 상태가 없다`() {
        val target = onboardedUser("noleak")
        tx.execute {
            val user = em.find(User::class.java, target.id)
            val breed = Breed.of(Species.CAT, "Korean").also { em.persist(it) }
            em.persist(PetProfile.register(user, breed, "cat", birthYearMonth = "2020-11", isDefault = true))
        }

        mockMvc
            .perform(get("/api/v1/sns/users/${target.publicId}"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.userId").doesNotExist())
            .andExpect(jsonPath("$.data.status").doesNotExist())
            .andExpect(jsonPath("$.data.pets[0].petId").doesNotExist())
            .andExpect(jsonPath("$.data.pets[0].weight").doesNotExist())
            .andExpect(jsonPath("$.data.pets[0].profileImageKey").doesNotExist())
            .andExpect(jsonPath("$.data.pets[0].version").doesNotExist())
    }

    private fun expectUnavailable(actions: ResultActions) {
        actions
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.errorCode").value(130000))
    }

    private data class Seeded(
        val id: Long,
        val publicId: String,
    )

    /** 온보딩을 마쳐 프로필 행이 있는 ACTIVE 사용자를 심는다. */
    private fun onboardedUser(
        nickname: String,
        bio: String? = null,
        imageKey: String? = null,
    ): Seeded =
        tx.execute {
            val user = User.preRegister()
            em.persist(user)
            user.completeOnboarding(nickname)
            if (bio != null || imageKey != null) user.updateProfile(nickname, bio, imageKey)
            em.flush()
            Seeded(user.id, user.publicId)
        }!!
}
