package com.aechak.api.config

import com.aechak.api.support.IntegrationTestBase
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.web.FilterChainProxy
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

/**
 * 미디어 타입이 어긋난 요청의 응답을 고정한다.
 *
 * 전역 핸들러에 415·406 분기가 없으면 둘 다 클라이언트 실수인데 서버 장애로 둔갑한다.
 * 415 자리는 catch-all이 500으로 내보내고, 406 자리는 catch-all이 만든 500 봉투조차
 * Accept 협상에 걸려 쓸 수 없어 sendError가 /error로 넘기고 — 실 컨테이너에서는 거기서
 * 시큐리티가 401 "로그인이 필요합니다"를 쓴다. 요청 하나에 error 로그가 둘 남는다.
 */
class MediaTypeContractIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var context: WebApplicationContext

    @Autowired
    private lateinit var securityFilterChain: FilterChainProxy

    private lateinit var mockMvc: MockMvc
    private lateinit var token: String

    @BeforeEach
    fun setUp() {
        mockMvc =
            MockMvcBuilders
                .webAppContextSetup(context)
                .addFilters<DefaultMockMvcBuilder>(securityFilterChain)
                .build()
        token = mintAccessToken(createActiveUser())
    }

    @Test
    @DisplayName("본문 Content-Type을 읽을 수 없으면 415와 90008이고 지원 타입이 Accept 헤더에 실린다")
    fun unsupportedContentTypeReturnsUnsupportedMediaType() {
        mockMvc
            .perform(
                post("/api/v1/auth/refresh")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                    .contentType(MediaType.TEXT_PLAIN)
                    .content("{}"),
            ).andExpect(status().isUnsupportedMediaType)
            .andExpect(jsonPath("$.errorCode").value(90008))
            .andExpect(header().exists(HttpHeaders.ACCEPT))
    }

    @Test
    @DisplayName("Content-Type이 없는 본문 요청도 415다")
    fun missingContentTypeReturnsUnsupportedMediaType() {
        mockMvc
            .perform(
                post("/api/v1/auth/refresh")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                    .content("{}"),
            ).andExpect(status().isUnsupportedMediaType)
            .andExpect(jsonPath("$.errorCode").value(90008))
    }

    @Test
    @DisplayName("415 메시지에 내부 구현이 실리지 않는다")
    fun unsupportedContentTypeHidesInternals() {
        val body =
            mockMvc
                .perform(
                    post("/api/v1/auth/refresh")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("{}"),
                ).andReturn()
                .response
                .contentAsString

        assert(!body.contains("com.aechak")) { "패키지명이 응답에 실렸다: $body" }
        assert(!body.contains("org.springframework")) { "패키지명이 응답에 실렸다: $body" }
        assert(!body.contains("Content-Type")) { "스프링 기본 문구가 그대로 실렸다: $body" }
    }

    @Test
    @DisplayName("JSON을 받지 않겠다는 요청은 406과 90009이고 봉투는 JSON으로 나간다")
    fun unacceptableAcceptReturnsNotAcceptable() {
        mockMvc
            .perform(
                get("/api/v1/products")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                    .accept(MediaType.APPLICATION_XML),
            ).andExpect(status().isNotAcceptable)
            // Accept 협상에 맡기면 이 봉투도 쓸 수 없어 /error로 떨어진다 — Content-Type을 못박았는지가 핵심.
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.errorCode").value(90009))
    }

    @Test
    @DisplayName("JSON을 받는 정상 요청은 그대로 200이다")
    fun acceptableRequestStillSucceeds() {
        mockMvc
            .perform(
                get("/api/v1/products?size=1")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                    .accept(MediaType.APPLICATION_JSON),
            ).andExpect(status().isOk)
    }
}
