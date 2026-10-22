package com.aechak.api.sns

import com.aechak.api.support.IntegrationTestBase
import com.aechak.domain.user.user.User
import com.jayway.jsonpath.JsonPath
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.security.web.FilterChainProxy
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.sql.Timestamp
import java.time.LocalDateTime

/**
 * 통합: 차단과 뮤트 관리 목록이 실 보안 필터체인과 MySQL을 지나 계약대로 최근순 커서 목록을 돌려주는지.
 * 깨지면 순서나 totalCount가 어긋나거나 탈퇴 사용자가 남거나 상대의 차단이 내 목록 행을 지우거나 남의 커서가 통과한다.
 */
class SnsListIntegrationTest : IntegrationTestBase() {
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
    fun `차단 관리 목록은 최근 차단순이고 행에 표시 정보를 싣는다`() {
        val me = onboardedUser("listme")
        val first = onboardedUser("first", imageKey = "users/profile/abc.webp")
        val second = onboardedUser("second")
        relate(me, first.publicId, BLOCKS)
        relate(me, second.publicId, BLOCKS)

        list(me, BLOCKS)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.items[*].publicId", contains(second.publicId, first.publicId)))
            .andExpect(jsonPath("$.data.items[1].nickname").value("first"))
            .andExpect(jsonPath("$.data.items[1].profileImageUrl").value("https://fake-cdn.local/users/profile/abc.webp"))
            .andExpect(jsonPath("$.data.items[0].profileImageUrl").value(nullValue()))
            .andExpect(jsonPath("$.data.totalCount").value(2))
            .andExpect(jsonPath("$.data.nextCursor").value(nullValue()))
            .andExpect(jsonPath("$.data.hasNext").value(false))
    }

    @Test
    fun `뮤트 관리 목록은 최근 뮤트순이다`() {
        val me = onboardedUser("mutelist")
        val first = onboardedUser("mfirst")
        val second = onboardedUser("msecond")
        relate(me, first.publicId, MUTES)
        relate(me, second.publicId, MUTES)

        list(me, MUTES)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.items[*].publicId", contains(second.publicId, first.publicId)))
            .andExpect(jsonPath("$.data.totalCount").value(2))
    }

    @Test
    fun `첫 페이지에만 totalCount가 있고 커서로 다음 페이지를 이어 받는다`() {
        val me = onboardedUser("pageme")
        val targets = (1..3).map { onboardedUser("page$it") }
        targets.forEach { relate(me, it.publicId, BLOCKS) }

        val firstPage =
            list(me, BLOCKS, size = 2)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.items[*].publicId", contains(targets[2].publicId, targets[1].publicId)))
                .andExpect(jsonPath("$.data.totalCount").value(3))
                .andExpect(jsonPath("$.data.hasNext").value(true))
        val cursor = JsonPath.read<String>(firstPage.andReturn().response.contentAsString, "$.data.nextCursor")

        list(me, BLOCKS, size = 2, cursor = cursor)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.items[*].publicId", contains(targets[0].publicId)))
            .andExpect(jsonPath("$.data.totalCount").value(nullValue()))
            .andExpect(jsonPath("$.data.nextCursor").value(nullValue()))
            .andExpect(jsonPath("$.data.hasNext").value(false))
    }

    @Test
    fun `탈퇴한 사용자는 목록과 totalCount에서 빠진다`() {
        val me = onboardedUser("leaveme")
        val stay = onboardedUser("stay")
        val gone = onboardedUser("gone")
        relate(me, stay.publicId, BLOCKS)
        relate(me, gone.publicId, BLOCKS)
        tx.execute { em.find(User::class.java, gone.id).withdraw() }

        list(me, BLOCKS)
            .andExpect(jsonPath("$.data.items[*].publicId", contains(stay.publicId)))
            .andExpect(jsonPath("$.data.totalCount").value(1))
    }

    @Test
    fun `행의 createdAt은 관계 행이 생긴 시각이다`() {
        val me = onboardedUser("timeme")
        val target = onboardedUser("timed")
        val before = dbNow()
        relate(me, target.publicId, MUTES)
        val after = dbNow()
        val stored =
            tx.execute {
                em
                    .createNativeQuery("SELECT created_at FROM sns_mutes WHERE muter_id = ?1")
                    .setParameter(1, me.id)
                    .singleResult as LocalDateTime
            }!!
        assertTrue(
            !stored.isBefore(before) && !stored.isAfter(after),
            "저장된 created_at($stored)이 관계를 만든 요청 전후의 DB 시각($before ~ $after) 사이여야 한다",
        )

        val body =
            list(me, MUTES)
                .andExpect(status().isOk)
                .andReturn()
                .response.contentAsString

        assertEquals(
            stored,
            LocalDateTime.parse(JsonPath.read<String>(body, "$.data.items[0].createdAt")),
            "목록 createdAt이 저장된 관계 생성 시각과 같아야 한다",
        )
    }

    @Test
    fun `뮤트한 상대가 나를 차단해도 내 뮤트 목록에 남는다`() {
        val me = onboardedUser("mutekeep")
        val other = onboardedUser("muteblock")
        relate(me, other.publicId, MUTES)
        relate(other, me.publicId, BLOCKS)

        list(me, MUTES)
            .andExpect(jsonPath("$.data.items[*].publicId", contains(other.publicId)))
            .andExpect(jsonPath("$.data.totalCount").value(1))
    }

    @Test
    fun `차단한 상대가 나를 차단해도 내 차단 목록에 남고 totalCount에 들어간다`() {
        val me = onboardedUser("blockkeep")
        val other = onboardedUser("blockback")
        relate(me, other.publicId, BLOCKS)
        relate(other, me.publicId, BLOCKS)

        list(me, BLOCKS)
            .andExpect(jsonPath("$.data.items[*].publicId", contains(other.publicId)))
            .andExpect(jsonPath("$.data.totalCount").value(1))
    }

    @Test
    fun `잘못된 커서와 다른 종류 목록의 커서는 400 90002다`() {
        val me = onboardedUser("badcursor")
        (1..2).map { onboardedUser("bad$it") }.forEach { relate(me, it.publicId, MUTES) }
        val muteCursor = nextCursor(list(me, MUTES, size = 1))

        expectInvalidCursor(list(me, BLOCKS, cursor = "%%%broken%%%"))
        expectInvalidCursor(list(me, BLOCKS, cursor = muteCursor))
    }

    @Test
    fun `다른 사용자의 차단 목록 커서를 내 차단 목록에 넣으면 400 90002다`() {
        val me = onboardedUser("cursorme")
        val other = onboardedUser("cursorother")
        (1..2).map { onboardedUser("oc$it") }.forEach { relate(other, it.publicId, BLOCKS) }
        val othersCursor = nextCursor(list(other, BLOCKS, size = 1))

        expectInvalidCursor(list(me, BLOCKS, cursor = othersCursor))
    }

    @Test
    fun `size가 범위를 벗어나면 400 90001이다`() {
        val me = onboardedUser("sizeme")

        list(me, BLOCKS, size = 101)
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(90001))
    }

    private fun dbNow(): LocalDateTime =
        tx.execute {
            when (val now = em.createNativeQuery("SELECT NOW(6)").singleResult) {
                is LocalDateTime -> now
                is Timestamp -> now.toLocalDateTime()
                else -> error("NOW(6) 결과 타입을 예상하지 못했다: ${now?.javaClass}")
            }
        }!!

    private fun relate(
        actor: Seeded,
        targetPublicId: String,
        kind: String,
    ) {
        val action = if (kind == BLOCKS) "block" else "mute"
        mockMvc
            .perform(
                post("/api/v1/sns/users/$targetPublicId/$action")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer ${mintAccessToken(actor.id)}"),
            ).andExpect(status().isCreated)
    }

    private fun list(
        viewer: Seeded,
        kind: String,
        size: Int? = null,
        cursor: String? = null,
    ): ResultActions {
        val request =
            get("/api/v1/sns/me/$kind")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${mintAccessToken(viewer.id)}")
        size?.let { request.param("size", it.toString()) }
        cursor?.let { request.param("cursor", it) }
        return mockMvc.perform(request)
    }

    private fun nextCursor(actions: ResultActions): String =
        JsonPath.read(
            actions
                .andExpect(status().isOk)
                .andReturn()
                .response.contentAsString,
            "$.data.nextCursor",
        )

    private fun expectInvalidCursor(actions: ResultActions) {
        actions
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(90002))
    }

    private data class Seeded(
        val id: Long,
        val publicId: String,
    )

    /** 온보딩을 마쳐 프로필 행이 있는 ACTIVE 사용자를 심는다. */
    private fun onboardedUser(
        nickname: String,
        imageKey: String? = null,
    ): Seeded =
        tx.execute {
            val user = User.preRegister()
            em.persist(user)
            user.completeOnboarding(nickname)
            if (imageKey != null) user.updateProfile(nickname, null, imageKey)
            em.flush()
            Seeded(user.id, user.publicId)
        }!!

    companion object {
        private const val BLOCKS = "blocks"
        private const val MUTES = "mutes"
    }
}
