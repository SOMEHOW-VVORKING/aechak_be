package com.aechak.api.sns

import com.aechak.api.support.SwitchableRateLimitCounter
import com.aechak.application.ratelimit.facade.RateLimitFacade
import com.aechak.domain.support.Ulid
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.HttpHeaders
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 통합: SNS 쓰기 요청이 G10 수치를 넘으면 429 90012이고 거절된 요청은 어느 창의 수도 쓰지 않는지,
 * 이미 게시물이 생긴 같은 멱등키 재요청은 게시 한도를 쓰지 않는지, 카운터 키가 창 길이 안에 만료되는지.
 * 깨지면 반복 요청이 통계 행을 계속 잠그거나 응답을 못 받은 재게시가 한도를 쓰거나 만료 없는 키가 사용자를 계속 막는다.
 */
class SnsRateLimitIntegrationTest : SnsPostIntegrationTestBase() {
    @Autowired
    private lateinit var counter: SwitchableRateLimitCounter

    @Autowired
    private lateinit var redisTemplate: StringRedisTemplate

    // 사용자 id는 테스트마다 1부터 다시 쓰여 앞 테스트의 카운터를 지운다
    @BeforeEach
    fun enableRateLimit() {
        redisTemplate.delete(redisTemplate.keys("${RateLimitFacade.PREFIX}*"))
        counter.enabled = true
    }

    @AfterEach
    fun disableRateLimit() {
        counter.enabled = false
    }

    @Test
    fun `팔로우는 1분 31번째가 429이고 거절된 요청은 1일 수를 쓰지 않으며 언팔로우는 따로 센다`() {
        val me = onboardedUser()
        val target = publicIdOf(onboardedUser())

        repeat(30) { relation("follow", me, target, on = true).andExpect(status().isCreated) }
        relation("follow", me, target, on = true)
            .andExpect(status().isTooManyRequests)
            .andExpect(jsonPath("$.errorCode").value(90012))

        assertEquals("30", counterValue("sns:FOLLOW", me, 86_400), "거절된 팔로우는 1일 수를 올리지 않아야 한다")
        relation("follow", me, target, on = false).andExpect(status().isNoContent)
    }

    @ParameterizedTest
    @ValueSource(strings = ["follow", "block", "mute"])
    fun `켜기와 끄기는 행동마다 1분 30건이다`(action: String) {
        val me = onboardedUser()
        val target = publicIdOf(onboardedUser())

        repeat(30) { relation(action, me, target, on = false).andExpect(status().isNoContent) }
        relation(action, me, target, on = false).andExpect(status().isTooManyRequests)
        if (action != "follow") {
            repeat(30) { relation(action, me, target, on = true).andExpect(status().isCreated) }
            relation(action, me, target, on = true).andExpect(status().isTooManyRequests)
        }
    }

    @Test
    fun `게시는 1분 6번째가 429이고 게시물이 생긴 같은 키 재요청은 세지 않는다`() {
        val me = onboardedUser()
        val firstKey = Ulid.generate()
        val first = createdPublicId(me, idempotencyKey = firstKey)
        repeat(4) { createdPublicId(me) }

        assertEquals(first, createdPublicId(me, idempotencyKey = firstKey), "같은 키 재요청은 한도와 상관없이 그 게시물을 돌려줘야 한다")
        createPost(me, postBody())
            .andExpect(status().isTooManyRequests)
            .andExpect(jsonPath("$.errorCode").value(90012))

        assertEquals(5, postCountOf(me), "거절된 게시는 게시물을 만들지 않아야 한다")
        assertEquals("5", counterValue("sns:POST_CREATE", me, 3_600), "1시간 수는 만든 게시물 수와 같아야 한다")
    }

    @Test
    fun `수정은 1분 11번째가 429다`() {
        val me = onboardedUser()
        val post = createdPublicId(me)

        repeat(10) { updatePost(post, me, updateBody()).andExpect(status().isNoContent) }
        updatePost(post, me, updateBody()).andExpect(status().isTooManyRequests)
    }

    @Test
    fun `리포스트와 저장은 켜기와 끄기를 합쳐 1분 61번째가 429다`() {
        val me = onboardedUser()
        val post = createdPublicId(onboardedUser())

        repeat(30) {
            repost(post, me).andExpect(status().isCreated)
            unrepost(post, me).andExpect(status().isNoContent)
            save(post, me).andExpect(status().isCreated)
            unsave(post, me).andExpect(status().isNoContent)
        }
        repost(post, me).andExpect(status().isTooManyRequests)
        save(post, me).andExpect(status().isTooManyRequests)
    }

    @Test
    fun `카운터 키는 창마다 하나이고 창 길이 안에 만료된다`() {
        val me = onboardedUser()
        createdPublicId(me)

        val keys = redisTemplate.keys("${RateLimitFacade.PREFIX}*")
        assertEquals(2, keys.size, "게시는 1분과 1시간 창을 하나씩 세야 한다: $keys")
        keys.forEach { key ->
            val windowSeconds = key.split(":")[4].toLong()
            val ttl = redisTemplate.getExpire(key)
            assertTrue(ttl in 1..windowSeconds, "$key 는 창 길이 안에 만료되어야 한다: $ttl")
        }
    }

    private fun relation(
        action: String,
        userId: Long,
        targetPublicId: String,
        on: Boolean,
    ): ResultActions {
        val path = "/api/v1/sns/users/$targetPublicId/$action"
        val request = if (on) post(path) else delete(path)
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer ${mintAccessToken(userId)}"))
    }

    private fun counterValue(
        rule: String,
        userId: Long,
        windowSeconds: Long,
    ): String? = redisTemplate.opsForValue().get("${RateLimitFacade.PREFIX}$rule:$userId:$windowSeconds:0")
}
