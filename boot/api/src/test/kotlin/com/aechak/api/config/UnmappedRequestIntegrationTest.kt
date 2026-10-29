package com.aechak.api.config

import com.aechak.api.support.IntegrationTestBase
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.security.web.FilterChainProxy
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

/**
 * 매핑이 없는 요청의 응답을 고정한다.
 *
 * 전역 핸들러에 404·405 분기가 없으면 catch-all이 전부 500으로 내보내고, 요청마다 error 로그와
 * 스택트레이스가 쌓인다. 클라이언트는 자기 경로 오타와 서버 장애를 구별할 수 없고 5xx 경보도 오염된다.
 */
class UnmappedRequestIntegrationTest : IntegrationTestBase() {
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
    @DisplayName("매핑이 없는 경로는 404와 90006이다")
    fun unmappedPathReturnsNotFound() {
        mockMvc
            .perform(get("/api/v1/no-such-path").header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.errorCode").value(90006))
    }

    @Test
    @DisplayName("경로는 있지만 그 메서드가 없으면 405와 90007이고 Allow 헤더가 실린다")
    fun unsupportedMethodReturnsMethodNotAllowed() {
        mockMvc
            .perform(delete("/api/v1/products").header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
            .andExpect(status().isMethodNotAllowed)
            .andExpect(jsonPath("$.errorCode").value(90007))
            .andExpect(header().exists(HttpHeaders.ALLOW))
    }

    @Test
    @DisplayName("쿼리 파라미터 타입이 어긋나면 400이고 메시지에 내부 타입명이 실리지 않는다")
    fun typeMismatchHidesInternalTypeName() {
        val body =
            mockMvc
                .perform(get("/api/v1/products?size=abc").header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.errorCode").value(90001))
                .andReturn()
                .response
                .contentAsString

        // "Failed to convert value of type 'java.lang.String' to required type 'int'" 가 그대로 나가던 자리
        assert(!body.contains("java.lang")) { "내부 타입명이 응답에 실렸다: $body" }
        assert(!body.contains("Failed to convert")) { "스프링 기본 문구가 그대로 실렸다: $body" }
        assert(body.contains("size")) { "어느 파라미터가 잘못됐는지 알 수 없다: $body" }
    }

    @Test
    @DisplayName("제약 위반 메시지는 그대로 유지된다")
    fun constraintMessageStaysIntact() {
        mockMvc
            .perform(get("/api/v1/products?size=-5").header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(90001))
            .andExpect(jsonPath("$.message").value("size는 1~100 사이여야 합니다."))
    }

    @Test
    @DisplayName("정상 요청은 그대로 200이다")
    fun mappedRequestStillSucceeds() {
        mockMvc
            .perform(get("/api/v1/products?size=1").header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
            .andExpect(status().isOk)
    }
}
