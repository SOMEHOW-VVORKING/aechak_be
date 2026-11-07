package com.aechak.api.file

import com.aechak.api.support.IntegrationTestBase
import com.aechak.api.support.SwitchableRateLimitCounter
import com.aechak.application.ratelimit.facade.RateLimitFacade
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.endsWith
import org.hamcrest.Matchers.startsWith
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.web.FilterChainProxy
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

/**
 * 통합 — presigned 발급이 용도별 길이 규칙(필수, 상한, 하한)을 지키고 기존 용도의 요청과 응답은 그대로인지.
 */
class PresignedUrlIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var context: WebApplicationContext

    @Autowired
    private lateinit var securityFilterChain: FilterChainProxy

    @Autowired
    private lateinit var counter: SwitchableRateLimitCounter

    @Autowired
    private lateinit var redisTemplate: StringRedisTemplate

    private lateinit var mockMvc: MockMvc
    private var userId = 0L

    @BeforeEach
    fun setUp() {
        mockMvc =
            MockMvcBuilders
                .webAppContextSetup(context)
                .addFilters<DefaultMockMvcBuilder>(securityFilterChain)
                .build()
        userId = createActiveUser()
    }

    @Test
    fun `게시물 사진에 길이가 없으면 90001이다`() {
        issue("""{"purpose":"SNS_POST_IMAGE","contentType":"image/jpeg"}""")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(90001))
    }

    @Test
    fun `게시물 사진이 10MB를 1바이트 넘으면 100004다`() {
        issue("""{"purpose":"SNS_POST_IMAGE","contentType":"image/jpeg","contentLength":10485761}""")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(100004))
    }

    @Test
    fun `게시물 사진이 정확히 10MB면 발급한다`() {
        issue("""{"purpose":"SNS_POST_IMAGE","contentType":"image/jpeg","contentLength":10485760}""")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.url").isString)
            .andExpect(jsonPath("$.data.key").value(startsWith("tmp/$userId/sns/posts/images/")))
    }

    @Test
    fun `리뷰 사진은 길이 없이 발급하고 응답 모양이 그대로다`() {
        issue("""{"purpose":"REVIEW_IMAGE","contentType":"image/png"}""")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.length()").value(2))
            .andExpect(jsonPath("$.data.url").isString)
            .andExpect(jsonPath("$.data.key").value(startsWith("tmp/$userId/reviews/images/")))
    }

    @Test
    fun `리뷰 사진에도 길이 0을 보내면 90001이다`() {
        issue("""{"purpose":"REVIEW_IMAGE","contentType":"image/png","contentLength":0}""")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(90001))
    }

    @Test
    fun `게시물 사진에 허용 밖 형식을 보내면 100000이다`() {
        issue("""{"purpose":"SNS_POST_IMAGE","contentType":"video/webm","contentLength":1024}""")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(100000))
    }

    @Test
    fun `게시물 사진에 영상 형식을 보내면 100000이다`() {
        issue("""{"purpose":"SNS_POST_IMAGE","contentType":"video/mp4","contentLength":1024}""")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(100000))
    }

    @Test
    fun `게시물 영상에 길이가 없으면 90001이다`() {
        issue("""{"purpose":"SNS_POST_VIDEO","contentType":"video/mp4"}""")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(90001))
    }

    @Test
    fun `게시물 영상이 100MB를 1바이트 넘으면 100004다`() {
        issue("""{"purpose":"SNS_POST_VIDEO","contentType":"video/mp4","contentLength":104857601}""")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(100004))
    }

    @Test
    fun `게시물 영상은 MP4와 MOV 모두 정확히 100MB면 발급한다`() {
        issue("""{"purpose":"SNS_POST_VIDEO","contentType":"video/mp4","contentLength":104857600}""")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.key").value(allOf(startsWith("tmp/$userId/sns/posts/videos/"), endsWith(".mp4"))))
        issue("""{"purpose":"SNS_POST_VIDEO","contentType":"video/quicktime","contentLength":104857600}""")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.key").value(allOf(startsWith("tmp/$userId/sns/posts/videos/"), endsWith(".mov"))))
    }

    @Test
    fun `게시물 영상에 허용 밖 형식을 보내면 100000이다`() {
        issue("""{"purpose":"SNS_POST_VIDEO","contentType":"video/webm","contentLength":1024}""")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(100000))
    }

    @Test
    fun `발급은 용도와 상관없이 1분 61번째가 429 90012다`() {
        redisTemplate.delete(redisTemplate.keys("${RateLimitFacade.PREFIX}*"))
        counter.enabled = true

        repeat(30) { issue("""{"purpose":"REVIEW_IMAGE","contentType":"image/png"}""").andExpect(status().isOk) }
        repeat(30) {
            issue("""{"purpose":"SNS_POST_IMAGE","contentType":"image/jpeg","contentLength":1}""").andExpect(status().isOk)
        }
        issue("""{"purpose":"USER_PROFILE","contentType":"image/png"}""")
            .andExpect(status().isTooManyRequests)
            .andExpect(jsonPath("$.errorCode").value(90012))
    }

    @AfterEach
    fun disableRateLimit() {
        counter.enabled = false
    }

    private fun issue(body: String): ResultActions =
        mockMvc.perform(
            post("/api/v1/files/presigned-url")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${mintAccessToken(userId)}")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body),
        )
}
