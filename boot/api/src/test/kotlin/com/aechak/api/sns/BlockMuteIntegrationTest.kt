package com.aechak.api.sns

import com.aechak.api.support.IntegrationTestBase
import com.aechak.domain.user.user.User
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.security.web.FilterChainProxy
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

/**
 * 통합: 차단 켜기와 끄기가 실 보안 필터체인과 MySQL을 지나 계약대로 멱등하게 동작하는지.
 * 깨지면 반복 요청이 오류를 내거나 상대가 건 차단이 지워지거나 대상 판정이 계약과 달라진다.
 */
class BlockMuteIntegrationTest : IntegrationTestBase() {
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
    fun `차단은 201이고 반복해도 201이며 행은 하나다`() {
        val me = onboardedUser("blocker")
        val target = onboardedUser("blocked")

        block(me, target.publicId).andExpect(status().isCreated)
        block(me, target.publicId).andExpect(status().isCreated)

        assertEquals(1L, blockCount(me.id, target.id), "반복 차단이 차단 행을 하나만 남겨야 한다")
    }

    @Test
    fun `차단 해제는 204이고 차단한 적 없어도 204다`() {
        val me = onboardedUser("unblocker")
        val target = onboardedUser("unblocked")
        block(me, target.publicId).andExpect(status().isCreated)

        unblock(me, target.publicId).andExpect(status().isNoContent)
        assertEquals(0L, blockCount(me.id, target.id), "내가 건 차단 행이 남아 있으면 안 된다")

        unblock(me, target.publicId).andExpect(status().isNoContent)
    }

    @Test
    fun `본인 차단은 400 130100이다`() {
        val me = onboardedUser("selfblock")

        block(me, me.publicId)
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(130100))
        assertEquals(0L, blockCount(me.id, me.id), "본인 차단 행이 생기면 안 된다")
    }

    @Test
    fun `나를 차단한 사용자도 차단할 수 있다`() {
        val me = onboardedUser("mutualme")
        val other = onboardedUser("mutualother")
        block(other, me.publicId).andExpect(status().isCreated)

        block(me, other.publicId).andExpect(status().isCreated)

        assertEquals(1L, blockCount(me.id, other.id), "내가 건 차단 행이 하나 있어야 한다")
        assertEquals(1L, blockCount(other.id, me.id), "상대가 건 차단 행이 그대로 남아야 한다")
    }

    @Test
    fun `탈퇴 사용자 차단은 404 130000이다`() {
        val me = onboardedUser("towithdrawn")
        val target = onboardedUser("withdrawn")
        tx.execute { em.find(User::class.java, target.id).withdraw() }

        expectUnavailable(block(me, target.publicId))
        assertEquals(0L, blockCount(me.id, target.id), "내가 건 차단 행이 남아 있으면 안 된다")
    }

    @Test
    fun `온보딩 전 사용자와 없는 publicId 차단은 404 130000이다`() {
        val me = onboardedUser("topending")
        val pending = tx.execute { User.preRegister().also { em.persist(it) }.publicId }!!

        expectUnavailable(block(me, pending))
        expectUnavailable(block(me, "01JZZZZZZZZZZZZZZZZZZZZZZZ"))
    }

    @Test
    fun `차단 해제는 상대가 건 차단을 지우지 않는다`() {
        val me = onboardedUser("keepme")
        val other = onboardedUser("keepother")
        block(me, other.publicId).andExpect(status().isCreated)
        block(other, me.publicId).andExpect(status().isCreated)

        unblock(me, other.publicId).andExpect(status().isNoContent)

        assertEquals(0L, blockCount(me.id, other.id), "해제한 내 차단 행이 지워져야 한다")
        assertEquals(1L, blockCount(other.id, me.id), "상대가 건 차단 행이 그대로 남아야 한다")
    }

    @Test
    fun `탈퇴 사용자 차단 해제는 204이고 내 행을 지운다`() {
        val me = onboardedUser("leftme")
        val target = onboardedUser("leftone")
        block(me, target.publicId).andExpect(status().isCreated)
        tx.execute { em.find(User::class.java, target.id).withdraw() }

        unblock(me, target.publicId).andExpect(status().isNoContent)

        assertEquals(0L, blockCount(me.id, target.id), "내가 건 차단 행이 남아 있으면 안 된다")
    }

    @Test
    fun `없는 publicId 차단 해제는 404 130000이다`() {
        val me = onboardedUser("ghostme")

        expectUnavailable(unblock(me, "01JZZZZZZZZZZZZZZZZZZZZZZZ"))
    }

    private fun block(
        actor: Seeded,
        targetPublicId: String,
    ): ResultActions =
        mockMvc.perform(
            post("/api/v1/sns/users/$targetPublicId/block")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${mintAccessToken(actor.id)}"),
        )

    private fun unblock(
        actor: Seeded,
        targetPublicId: String,
    ): ResultActions =
        mockMvc.perform(
            delete("/api/v1/sns/users/$targetPublicId/block")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${mintAccessToken(actor.id)}"),
        )

    private fun blockCount(
        blockerId: Long,
        blockedId: Long,
    ): Long =
        tx.execute {
            (
                em
                    .createNativeQuery("SELECT COUNT(*) FROM sns_blocks WHERE blocker_id = ?1 AND blocked_id = ?2")
                    .setParameter(1, blockerId)
                    .setParameter(2, blockedId)
                    .singleResult as Number
            ).toLong()
        }!!

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
    private fun onboardedUser(nickname: String): Seeded =
        tx.execute {
            val user = User.preRegister()
            em.persist(user)
            user.completeOnboarding(nickname)
            em.flush()
            Seeded(user.id, user.publicId)
        }!!
}
