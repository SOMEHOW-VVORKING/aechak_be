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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

/**
 * 통합: 차단과 뮤트의 켜기와 끄기가 실 보안 필터체인과 MySQL을 지나 계약대로 멱등하게 동작하는지.
 * 깨지면 반복 요청이 오류를 내거나 상대가 건 차단이 지워지거나 차단이 뮤트를 지우거나 대상 판정이 계약과 달라지거나
 * 차단 뒤 팔로우가 남아 수가 관계 행 수와 어긋난다.
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

    @Test
    fun `서로 차단하면 두 사람 모두 상대 프로필이 404 130000이다`() {
        val me = onboardedUser("bothme")
        val other = onboardedUser("bothother")
        block(me, other.publicId).andExpect(status().isCreated)
        block(other, me.publicId).andExpect(status().isCreated)

        expectUnavailable(profile(me, other.publicId))
        expectUnavailable(profile(other, me.publicId))
    }

    @Test
    fun `서로 차단한 뒤 한쪽만 해제해도 해제한 사람에게 404가 이어진다`() {
        val me = onboardedUser("halfme")
        val other = onboardedUser("halfother")
        block(me, other.publicId).andExpect(status().isCreated)
        block(other, me.publicId).andExpect(status().isCreated)

        unblock(me, other.publicId).andExpect(status().isNoContent)

        expectUnavailable(profile(me, other.publicId))
        profile(other, me.publicId)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.blockedByMe").value(true))
    }

    @Test
    fun `서로 팔로우하던 사이를 차단하면 두 팔로우 행과 네 수가 정리된다`() {
        val me = onboardedUser("cutme")
        val other = onboardedUser("cutother")
        follow(me, other.publicId).andExpect(status().isCreated)
        follow(other, me.publicId).andExpect(status().isCreated)

        block(me, other.publicId).andExpect(status().isCreated)

        assertEquals(0L, followCount(me.id, other.id), "내가 건 팔로우 행이 지워져야 한다")
        assertEquals(0L, followCount(other.id, me.id), "상대가 건 팔로우 행이 지워져야 한다")
        assertEquals(listOf(0L, 0L), listOf(followerCountOf(me.id), followingCountOf(me.id)), "내 두 수가 0으로 줄어야 한다")
        assertEquals(listOf(0L, 0L), listOf(followerCountOf(other.id), followingCountOf(other.id)), "상대의 두 수가 0으로 줄어야 한다")
    }

    @Test
    fun `나를 팔로우하는 사람을 차단하면 내 팔로워 수만 1 준다`() {
        val me = onboardedUser("cutidol")
        val fan = onboardedUser("cutfan")
        val third = onboardedUser("cutthird")
        follow(fan, me.publicId).andExpect(status().isCreated)
        follow(me, third.publicId).andExpect(status().isCreated)

        block(me, fan.publicId).andExpect(status().isCreated)

        assertEquals(0L, followCount(fan.id, me.id), "차단한 사람이 건 팔로우 행이 지워져야 한다")
        assertEquals(0L, followerCountOf(me.id), "내 팔로워 수가 1 줄어야 한다")
        assertEquals(1L, followingCountOf(me.id), "다른 사람을 향한 내 팔로잉 수는 그대로여야 한다")
        assertEquals(0L, followingCountOf(fan.id), "차단당한 사람의 팔로잉 수가 1 줄어야 한다")
        assertEquals(1L, followCount(me.id, third.id), "차단과 무관한 내 팔로우 행은 남아야 한다")
        assertEquals(1L, followerCountOf(third.id), "차단과 무관한 사람의 팔로워 수는 그대로여야 한다")
    }

    @Test
    fun `차단 해제는 지운 팔로우를 되살리지 않는다`() {
        val me = onboardedUser("revme")
        val other = onboardedUser("revother")
        follow(me, other.publicId).andExpect(status().isCreated)
        follow(other, me.publicId).andExpect(status().isCreated)
        block(me, other.publicId).andExpect(status().isCreated)

        unblock(me, other.publicId).andExpect(status().isNoContent)

        assertEquals(0L, followCount(me.id, other.id), "차단 해제 뒤 내 팔로우 행이 다시 생기면 안 된다")
        assertEquals(0L, followCount(other.id, me.id), "차단 해제 뒤 상대 팔로우 행이 다시 생기면 안 된다")
        assertEquals(listOf(0L, 0L), listOf(followerCountOf(me.id), followingCountOf(me.id)), "차단 해제가 내 수를 되돌리면 안 된다")
        profile(me, other.publicId)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.following").value(false))
    }

    @Test
    fun `뮤트는 팔로우 행과 수를 바꾸지 않는다`() {
        val me = onboardedUser("mfme")
        val other = onboardedUser("mfother")
        follow(me, other.publicId).andExpect(status().isCreated)
        follow(other, me.publicId).andExpect(status().isCreated)

        mute(me, other.publicId).andExpect(status().isCreated)

        assertEquals(1L, followCount(me.id, other.id), "뮤트가 내 팔로우 행을 지우면 안 된다")
        assertEquals(1L, followCount(other.id, me.id), "뮤트가 상대 팔로우 행을 지우면 안 된다")
        assertEquals(listOf(1L, 1L), listOf(followerCountOf(me.id), followingCountOf(me.id)), "뮤트가 내 수를 바꾸면 안 된다")
        assertEquals(listOf(1L, 1L), listOf(followerCountOf(other.id), followingCountOf(other.id)), "뮤트가 상대 수를 바꾸면 안 된다")
    }

    @Test
    fun `뮤트는 201이고 반복해도 201이며 행은 하나다`() {
        val me = onboardedUser("muter")
        val target = onboardedUser("muted")

        mute(me, target.publicId).andExpect(status().isCreated)
        mute(me, target.publicId).andExpect(status().isCreated)

        assertEquals(1L, muteCount(me.id, target.id), "내 뮤트 행이 하나만 남아 있어야 한다")
    }

    @Test
    fun `뮤트 해제는 204이고 뮤트한 적 없어도 204다`() {
        val me = onboardedUser("unmuter")
        val target = onboardedUser("unmuted")
        mute(me, target.publicId).andExpect(status().isCreated)

        unmute(me, target.publicId).andExpect(status().isNoContent)
        assertEquals(0L, muteCount(me.id, target.id), "내가 건 뮤트 행이 남아 있으면 안 된다")

        unmute(me, target.publicId).andExpect(status().isNoContent)
    }

    @Test
    fun `본인 뮤트는 400 130200이다`() {
        val me = onboardedUser("selfmute")

        mute(me, me.publicId)
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(130200))
        assertEquals(0L, muteCount(me.id, me.id), "본인 뮤트 행이 생기면 안 된다")
    }

    @Test
    fun `나를 차단한 사용자도 뮤트할 수 있다`() {
        val me = onboardedUser("mbblockedme")
        val other = onboardedUser("mbblocker")
        block(other, me.publicId).andExpect(status().isCreated)

        mute(me, other.publicId).andExpect(status().isCreated)

        assertEquals(1L, muteCount(me.id, other.id), "나를 차단한 상대에게도 내 뮤트 행이 하나 생겨야 한다")
    }

    @Test
    fun `탈퇴 사용자 뮤트는 404 130000이다`() {
        val me = onboardedUser("mutetowd")
        val target = onboardedUser("mutewd")
        tx.execute { em.find(User::class.java, target.id).withdraw() }

        expectUnavailable(mute(me, target.publicId))
        assertEquals(0L, muteCount(me.id, target.id), "탈퇴 사용자에게 뮤트 행이 생기면 안 된다")
    }

    @Test
    fun `뮤트한 뒤 차단하고 차단을 해제해도 뮤트가 남는다`() {
        val me = onboardedUser("mutekeep")
        val target = onboardedUser("mutekept")
        mute(me, target.publicId).andExpect(status().isCreated)

        block(me, target.publicId).andExpect(status().isCreated)
        unblock(me, target.publicId).andExpect(status().isNoContent)

        assertEquals(1L, muteCount(me.id, target.id), "내 뮤트 행이 하나만 남아 있어야 한다")
    }

    @Test
    fun `탈퇴 사용자 뮤트 해제는 204이고 내 행을 지운다`() {
        val me = onboardedUser("muteleftme")
        val target = onboardedUser("muteleft")
        mute(me, target.publicId).andExpect(status().isCreated)
        tx.execute { em.find(User::class.java, target.id).withdraw() }

        unmute(me, target.publicId).andExpect(status().isNoContent)

        assertEquals(0L, muteCount(me.id, target.id), "내가 건 뮤트 행이 남아 있으면 안 된다")
    }

    @Test
    fun `없는 publicId 뮤트 해제는 404 130000이다`() {
        val me = onboardedUser("muteghost")

        expectUnavailable(unmute(me, "01JZZZZZZZZZZZZZZZZZZZZZZZ"))
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

    private fun profile(
        viewer: Seeded,
        targetPublicId: String,
    ): ResultActions =
        mockMvc.perform(
            get("/api/v1/sns/users/$targetPublicId")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${mintAccessToken(viewer.id)}"),
        )

    private fun follow(
        actor: Seeded,
        targetPublicId: String,
    ): ResultActions =
        mockMvc.perform(
            post("/api/v1/sns/users/$targetPublicId/follow")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${mintAccessToken(actor.id)}"),
        )

    private fun followCount(
        followerId: Long,
        followeeId: Long,
    ): Long = count("SELECT COUNT(*) FROM sns_follows WHERE follower_id = ?1 AND followee_id = ?2", followerId, followeeId)

    private fun followerCountOf(userId: Long): Long =
        count("SELECT COALESCE(SUM(follower_count), 0) FROM sns_user_stats WHERE user_id = ?1", userId)

    private fun followingCountOf(userId: Long): Long =
        count("SELECT COALESCE(SUM(following_count), 0) FROM sns_user_stats WHERE user_id = ?1", userId)

    private fun count(
        sql: String,
        vararg params: Long,
    ): Long =
        tx.execute {
            val query = em.createNativeQuery(sql)
            params.forEachIndexed { i, p -> query.setParameter(i + 1, p) }
            (query.singleResult as Number).toLong()
        }!!

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

    private fun mute(
        actor: Seeded,
        targetPublicId: String,
    ): ResultActions =
        mockMvc.perform(
            post("/api/v1/sns/users/$targetPublicId/mute")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${mintAccessToken(actor.id)}"),
        )

    private fun unmute(
        actor: Seeded,
        targetPublicId: String,
    ): ResultActions =
        mockMvc.perform(
            delete("/api/v1/sns/users/$targetPublicId/mute")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${mintAccessToken(actor.id)}"),
        )

    private fun muteCount(
        muterId: Long,
        mutedId: Long,
    ): Long =
        tx.execute {
            (
                em
                    .createNativeQuery("SELECT COUNT(*) FROM sns_mutes WHERE muter_id = ?1 AND muted_id = ?2")
                    .setParameter(1, muterId)
                    .setParameter(2, mutedId)
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
