package com.aechak.api.sns

import com.aechak.api.support.IntegrationTestBase
import com.aechak.application.sns.facade.SnsFacade
import com.aechak.application.sns.usecase.SnsUseCase
import com.aechak.application.sns.usecase.command.FollowUserCommand
import com.aechak.domain.sns.block.repository.BlockRepository
import com.aechak.domain.user.user.User
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.security.web.FilterChainProxy
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.context.WebApplicationContext
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 통합: 팔로우와 언팔로우가 실 보안 필터체인과 MySQL을 지나 계약대로 멱등하게 동작하고 두 사람의 수가 관계 행 수와 같게 움직이는지.
 * 깨지면 반복 요청이나 동시 요청이 수를 어긋나게 하거나 차단 관계에서 팔로우가 생기거나 대상 판정이 계약과 달라진다.
 */
class FollowIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var context: WebApplicationContext

    @Autowired
    private lateinit var securityFilterChain: FilterChainProxy

    @Autowired
    private lateinit var snsUseCase: SnsUseCase

    @Autowired
    private lateinit var blockRepository: BlockRepository

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

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
    fun `팔로우는 201이고 두 사람의 수를 1씩 올리며 언팔로우는 204이고 다시 내린다`() {
        val me = onboardedUser("follower")
        val target = onboardedUser("followee")

        follow(me, target.publicId).andExpect(status().isCreated)

        assertEquals(1L, followCount(me.id, target.id), "팔로우 행이 하나 생겨야 한다")
        assertEquals(1L, followerCountOf(target.id), "상대 팔로워 수가 1이어야 한다")
        assertEquals(1L, followingCountOf(me.id), "내 팔로잉 수가 1이어야 한다")
        assertEquals(0L, followerCountOf(me.id), "내 팔로워 수는 바뀌면 안 된다")

        unfollow(me, target.publicId).andExpect(status().isNoContent)

        assertEquals(0L, followCount(me.id, target.id), "언팔로우 뒤 팔로우 행이 남으면 안 된다")
        assertEquals(0L, followerCountOf(target.id), "상대 팔로워 수가 0으로 돌아와야 한다")
        assertEquals(0L, followingCountOf(me.id), "내 팔로잉 수가 0으로 돌아와야 한다")
    }

    @Test
    fun `반복 팔로우와 반복 언팔로우는 수를 바꾸지 않는다`() {
        val me = onboardedUser("repeatme")
        val target = onboardedUser("repeatyou")

        follow(me, target.publicId).andExpect(status().isCreated)
        follow(me, target.publicId).andExpect(status().isCreated)

        assertEquals(1L, followCount(me.id, target.id), "반복 팔로우가 행을 하나만 남겨야 한다")
        assertEquals(1L, followerCountOf(target.id), "반복 팔로우가 상대 팔로워 수를 더 올리면 안 된다")
        assertEquals(1L, followingCountOf(me.id), "반복 팔로우가 내 팔로잉 수를 더 올리면 안 된다")

        unfollow(me, target.publicId).andExpect(status().isNoContent)
        unfollow(me, target.publicId).andExpect(status().isNoContent)

        assertEquals(0L, followerCountOf(target.id), "반복 언팔로우 뒤 상대 팔로워 수는 0이어야 한다")
        assertEquals(0L, followingCountOf(me.id), "반복 언팔로우 뒤 내 팔로잉 수는 0이어야 한다")
    }

    @Test
    fun `팔로우한 적 없는 언팔로우는 204이고 수를 바꾸지 않는다`() {
        val me = onboardedUser("nevermet")
        val target = onboardedUser("stranger")
        follow(target, me.publicId).andExpect(status().isCreated)

        unfollow(me, target.publicId).andExpect(status().isNoContent)

        assertEquals(1L, followCount(target.id, me.id), "상대가 건 팔로우 행은 지워지면 안 된다")
        assertEquals(1L, followerCountOf(me.id), "상대가 건 팔로우의 수는 그대로여야 한다")
        assertEquals(0L, followingCountOf(me.id), "내 팔로잉 수는 0이어야 한다")
    }

    @Test
    fun `탈퇴 사용자 언팔로우는 204이고 내 행과 수를 정리한다`() {
        val me = onboardedUser("wdfollower")
        val target = onboardedUser("wdfollowee")
        follow(me, target.publicId).andExpect(status().isCreated)
        tx.execute { em.find(User::class.java, target.id).withdraw() }

        unfollow(me, target.publicId).andExpect(status().isNoContent)

        assertEquals(0L, followCount(me.id, target.id), "탈퇴한 상대를 향한 내 팔로우 행이 지워져야 한다")
        assertEquals(0L, followingCountOf(me.id), "내 팔로잉 수가 0으로 돌아와야 한다")
    }

    @Test
    fun `없는 publicId 언팔로우는 404 130000이다`() {
        val me = onboardedUser("ghostfan")

        expectUnavailable(unfollow(me, MISSING_PUBLIC_ID))
    }

    @Test
    fun `수가 0인데 팔로우 행이 남아 있어도 언팔로우가 수를 0 아래로 내리지 않는다`() {
        val me = onboardedUser("zerome")
        val target = onboardedUser("zeroyou")
        insertFollowRowOnly(me.id, target.id)

        unfollow(me, target.publicId).andExpect(status().isNoContent)

        assertEquals(0L, followCount(me.id, target.id), "팔로우 행은 지워져야 한다")
        assertEquals(0L, followerCountOf(target.id), "상대 팔로워 수가 0 아래로 내려가면 안 된다")
        assertEquals(0L, followingCountOf(me.id), "내 팔로잉 수가 0 아래로 내려가면 안 된다")
    }

    @Test
    fun `본인 팔로우는 400 130300이다`() {
        val me = onboardedUser("selffollow")

        follow(me, me.publicId)
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(130300))
        assertEquals(0L, followCount(me.id, me.id), "본인 팔로우 행이 생기면 안 된다")
        assertEquals(0L, followerCountOf(me.id), "본인 팔로우가 수를 바꾸면 안 된다")
    }

    @Test
    fun `탈퇴 사용자, 온보딩 전 사용자, 없는 publicId 팔로우는 404 130000이다`() {
        val me = onboardedUser("tounseen")
        val withdrawn = onboardedUser("gonefolk")
        tx.execute { em.find(User::class.java, withdrawn.id).withdraw() }
        val pending = tx.execute { User.preRegister().also { em.persist(it) }.publicId }!!

        expectUnavailable(follow(me, withdrawn.publicId))
        expectUnavailable(follow(me, pending))
        expectUnavailable(follow(me, MISSING_PUBLIC_ID))
        assertEquals(0L, followCount(me.id, withdrawn.id), "탈퇴 사용자에게 팔로우 행이 생기면 안 된다")
        assertEquals(0L, followingCountOf(me.id), "거부된 팔로우가 내 팔로잉 수를 바꾸면 안 된다")
    }

    @Test
    fun `나를 차단한 사용자 팔로우는 404 130000이고 서로 차단한 상태도 같다`() {
        val me = onboardedUser("blockedfan")
        val other = onboardedUser("blockingidol")
        block(other, me.publicId).andExpect(status().isCreated)

        expectUnavailable(follow(me, other.publicId))

        block(me, other.publicId).andExpect(status().isCreated)
        expectUnavailable(follow(me, other.publicId))
        assertEquals(0L, followCount(me.id, other.id), "차단 관계에서 팔로우 행이 생기면 안 된다")
        assertEquals(0L, followerCountOf(other.id), "거부된 팔로우가 상대 팔로워 수를 바꾸면 안 된다")
    }

    @Test
    fun `내가 차단한 사용자 팔로우는 403 130001이다`() {
        val me = onboardedUser("blockerfan")
        val other = onboardedUser("blockedidol")
        block(me, other.publicId).andExpect(status().isCreated)

        follow(me, other.publicId)
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.errorCode").value(130001))
        assertEquals(0L, followCount(me.id, other.id), "내가 차단한 사용자에게 팔로우 행이 생기면 안 된다")
        assertEquals(0L, followingCountOf(me.id), "거부된 팔로우가 내 팔로잉 수를 바꾸면 안 된다")
    }

    @Test
    fun `나를 뮤트한 사람도 팔로우할 수 있다`() {
        val me = onboardedUser("mutedfan")
        val other = onboardedUser("mutingidol")
        mute(other, me.publicId).andExpect(status().isCreated)

        follow(me, other.publicId).andExpect(status().isCreated)

        assertEquals(1L, followCount(me.id, other.id), "나를 뮤트한 상대에게도 팔로우 행이 생겨야 한다")
        assertEquals(1L, followerCountOf(other.id), "상대 팔로워 수가 1이어야 한다")
    }

    @Test
    fun `프로필의 following은 팔로우하면 true이고 언팔로우하면 false다`() {
        val me = onboardedUser("profilefan")
        val target = onboardedUser("profileidol")

        follow(me, target.publicId).andExpect(status().isCreated)
        profile(me, target.publicId)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.following").value(true))
            .andExpect(jsonPath("$.data.followerCount").value(1))
        profile(target, me.publicId)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.following").value(false))

        unfollow(me, target.publicId).andExpect(status().isNoContent)
        profile(me, target.publicId)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.following").value(false))
    }

    @Test
    fun `findDirectionsForShare는 내가 건 차단과 상대가 건 차단을 함께 돌려준다`() {
        val me = onboardedUser("dirme")
        val other = onboardedUser("dirother")
        val third = onboardedUser("dirthird")
        block(me, other.publicId).andExpect(status().isCreated)
        block(other, me.publicId).andExpect(status().isCreated)
        block(third, me.publicId).andExpect(status().isCreated)

        val both = tx.execute { blockRepository.findDirectionsForShare(me.id, other.id) }!!
        val none = tx.execute { blockRepository.findDirectionsForShare(other.id, third.id) }!!

        assertEquals(setOf(me.id, other.id), both, "두 방향 차단의 차단한 사람이 모두 담겨야 한다")
        assertEquals(emptySet<Long>(), none, "두 사람 사이의 차단만 담고 다른 쌍의 차단은 담으면 안 된다")
    }

    @Nested
    inner class Concurrency {
        @Test
        fun `같은 쌍 동시 팔로우는 행 1개와 수 1만 남긴다`() {
            val me = onboardedUser("racefan")
            val target = onboardedUser("raceidol")

            val errors = runConcurrently(List(6) { { snsUseCase.follow(FollowUserCommand(me.id, target.publicId)) } })

            assertTrue(errors.isEmpty(), "동시 팔로우에서 오류가 나면 안 된다: $errors")
            assertEquals(1L, followCount(me.id, target.id), "동시 팔로우가 행을 하나만 남겨야 한다")
            assertEquals(1L, followerCountOf(target.id), "상대 팔로워 수가 1이어야 한다")
            assertEquals(1L, followingCountOf(me.id), "내 팔로잉 수가 1이어야 한다")
        }

        @Test
        fun `서로 팔로우하는 동시 요청은 교착 없이 두 행과 네 수를 맞춘다`() {
            val a = onboardedUser("mutuala")
            val b = onboardedUser("mutualb")

            val errors =
                runConcurrently(
                    List(6) { i ->
                        if (i % 2 == 0) {
                            { snsUseCase.follow(FollowUserCommand(a.id, b.publicId)) }
                        } else {
                            { snsUseCase.follow(FollowUserCommand(b.id, a.publicId)) }
                        }
                    },
                )

            assertTrue(errors.isEmpty(), "서로 팔로우하는 동시 요청에서 교착이나 오류가 나면 안 된다: $errors")
            assertEquals(1L, followCount(a.id, b.id), "a가 b를 팔로우하는 행이 하나여야 한다")
            assertEquals(1L, followCount(b.id, a.id), "b가 a를 팔로우하는 행이 하나여야 한다")
            assertEquals(listOf(1L, 1L), listOf(followerCountOf(a.id), followingCountOf(a.id)), "a의 두 수가 1이어야 한다")
            assertEquals(listOf(1L, 1L), listOf(followerCountOf(b.id), followingCountOf(b.id)), "b의 두 수가 1이어야 한다")
        }

        @Test
        fun `여러 사용자가 한 사용자를 동시에 팔로우해도 팔로워 수가 행 수와 같다`() {
            val idol = onboardedUser("popular")
            val fans = (1..8).map { onboardedUser("fan$it") }

            val errors = runConcurrently(fans.map { fan -> { snsUseCase.follow(FollowUserCommand(fan.id, idol.publicId)) } })

            assertTrue(errors.isEmpty(), "여러 사용자의 동시 팔로우에서 오류가 나면 안 된다: $errors")
            assertEquals(8L, followerRowCount(idol.id), "팬마다 팔로우 행이 하나씩 있어야 한다")
            assertEquals(followerRowCount(idol.id), followerCountOf(idol.id), "팔로워 수가 실제 팔로우 행 수와 같아야 한다")
        }

        @Test
        fun `팔로우하지 않은 쌍의 언팔로우가 진행 중이어도 다른 쌍의 팔로우가 갭 잠금에 막히지 않는다`() {
            val a = onboardedUser("gapa")
            val b = onboardedUser("gapb")
            val c = onboardedUser("gapc")
            val d = onboardedUser("gapd")
            // 언팔로우 트랜잭션을 열어 둔 채 다른 쌍을 팔로우한다. 격리 수준은 SnsFacade.unfollow 선언을 그대로 쓴다
            val unfollowTx =
                TransactionTemplate(transactionManager).apply {
                    isolationLevel = unfollowIsolation().value()
                }
            val deleted = CountDownLatch(1)
            val release = CountDownLatch(1)
            val pool = Executors.newFixedThreadPool(2)
            try {
                val holder =
                    pool.submit {
                        unfollowTx.execute {
                            snsUseCase.unfollow(FollowUserCommand(a.id, b.publicId))
                            deleted.countDown()
                            release.await(15, TimeUnit.SECONDS)
                        }
                    }
                assertTrue(deleted.await(10, TimeUnit.SECONDS), "언팔로우가 DELETE까지 진행해야 한다")

                val other = pool.submit { snsUseCase.follow(FollowUserCommand(c.id, d.publicId)) }
                val finished = runCatching { other.get(5, TimeUnit.SECONDS) }
                assertTrue(
                    finished.isSuccess,
                    "다른 쌍의 팔로우가 언팔로우 트랜잭션의 갭 잠금을 기다리면 안 된다: ${finished.exceptionOrNull()}",
                )

                release.countDown()
                holder.get(10, TimeUnit.SECONDS)
            } finally {
                release.countDown()
                pool.shutdownNow()
            }

            assertEquals(1L, followCount(c.id, d.id), "다른 쌍의 팔로우 행이 들어가야 한다")
            assertEquals(1L, followerCountOf(d.id), "다른 쌍 상대의 팔로워 수가 1이어야 한다")
        }

        private fun unfollowIsolation() =
            SnsFacade::class.java
                .getMethod("unfollow", FollowUserCommand::class.java)
                .getAnnotation(Transactional::class.java)
                .isolation
    }

    /** 작업을 스레드마다 하나씩 동시에 출발시키고 난 예외를 모은다 */
    private fun runConcurrently(tasks: List<() -> Unit>): List<Throwable> {
        val ready = CountDownLatch(tasks.size)
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(tasks.size)
        val errors = Collections.synchronizedList(mutableListOf<Throwable>())
        try {
            val futures =
                tasks.map { task ->
                    pool.submit {
                        ready.countDown()
                        start.await()
                        try {
                            task()
                        } catch (e: Throwable) {
                            errors.add(e)
                        }
                    }
                }
            ready.await(5, TimeUnit.SECONDS)
            start.countDown()
            futures.forEach { it.get(20, TimeUnit.SECONDS) }
        } finally {
            pool.shutdownNow()
        }
        return errors
    }

    private fun follow(
        actor: Seeded,
        targetPublicId: String,
    ): ResultActions = perform(post("/api/v1/sns/users/$targetPublicId/follow"), actor)

    private fun unfollow(
        actor: Seeded,
        targetPublicId: String,
    ): ResultActions = perform(delete("/api/v1/sns/users/$targetPublicId/follow"), actor)

    private fun block(
        actor: Seeded,
        targetPublicId: String,
    ): ResultActions = perform(post("/api/v1/sns/users/$targetPublicId/block"), actor)

    private fun mute(
        actor: Seeded,
        targetPublicId: String,
    ): ResultActions = perform(post("/api/v1/sns/users/$targetPublicId/mute"), actor)

    private fun profile(
        viewer: Seeded,
        targetPublicId: String,
    ): ResultActions = perform(get("/api/v1/sns/users/$targetPublicId"), viewer)

    private fun perform(
        request: MockHttpServletRequestBuilder,
        actor: Seeded,
    ): ResultActions = mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer ${mintAccessToken(actor.id)}"))

    private fun expectUnavailable(actions: ResultActions) {
        actions
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.errorCode").value(130000))
    }

    private fun followCount(
        followerId: Long,
        followeeId: Long,
    ): Long = count("SELECT COUNT(*) FROM sns_follows WHERE follower_id = ?1 AND followee_id = ?2", followerId, followeeId)

    private fun followerRowCount(followeeId: Long): Long = count("SELECT COUNT(*) FROM sns_follows WHERE followee_id = ?1", followeeId)

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

    /** 수는 그대로 두고 팔로우 행만 넣는다 */
    private fun insertFollowRowOnly(
        followerId: Long,
        followeeId: Long,
    ) {
        tx.execute {
            em
                .createNativeQuery(
                    "INSERT INTO sns_follows (follower_id, followee_id, created_at, updated_at) VALUES (?1, ?2, NOW(6), NOW(6))",
                ).setParameter(1, followerId)
                .setParameter(2, followeeId)
                .executeUpdate()
        }
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

    companion object {
        private const val MISSING_PUBLIC_ID = "01JZZZZZZZZZZZZZZZZZZZZZZZ"
    }
}
