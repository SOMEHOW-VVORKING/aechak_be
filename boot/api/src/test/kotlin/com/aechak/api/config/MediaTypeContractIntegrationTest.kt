package com.aechak.api.config

import com.aechak.api.support.IntegrationTestBase
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.web.FilterChainProxy
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
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
 * 미디어 타입이 어긋난 요청의 응답과, 실패 봉투가 Accept에 눌리지 않는 것을 고정한다.
 *
 * 전부 **인증 없이 열린 경로를 토큰 없이** 때린다 — 토큰을 달면 결함의 핵심 입력이 아니다.
 * 전역 핸들러에 415·406 분기가 없으면 클라이언트 실수가 서버 장애로 둔갑하고, 실패 봉투가
 * Content-Type을 못박지 않으면 404·400 같은 평범한 오류조차 Accept 하나로 봉투째 사라진다
 * (실 컨테이너에서는 ERROR 디스패치의 시큐리티가 그 자리에 401 "로그인이 필요합니다"를 쓴다).
 *
 * MockMvc는 컨테이너 ERROR 디스패치를 재현하지 못하므로 401 자체는 여기서 볼 수 없다.
 * 대신 **MVC가 봉투를 끝까지 썼는지**를 본다 — 그걸 쓰는 한 sendError가 호출되지 않아 401 경로가 열리지 않는다.
 */
class MediaTypeContractIntegrationTest : IntegrationTestBase() {
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
    @DisplayName("본문 Content-Type을 읽을 수 없으면 415와 90008이고 지원 타입을 Accept 헤더로 알려준다")
    fun unsupportedContentTypeReturnsUnsupportedMediaType() {
        mockMvc
            .perform(
                post(REFRESH)
                    .contentType(MediaType.TEXT_PLAIN)
                    .content("{}"),
            ).expectEnvelope(415, 90008)
            // 존재만 보면 값이 엉뚱해도 통과한다 — JSON을 실제로 알려주는지 본다.
            .andExpect(header().string(HttpHeaders.ACCEPT, containsString(MediaType.APPLICATION_JSON_VALUE)))
    }

    @Test
    @DisplayName("Content-Type이 없는 본문 요청도 415다")
    fun missingContentTypeReturnsUnsupportedMediaType() {
        mockMvc.perform(post(REFRESH).content("{}")).expectEnvelope(415, 90008)
    }

    @Test
    @DisplayName("415 메시지에 내부 구현이 실리지 않는다")
    fun unsupportedContentTypeHidesInternals() {
        val body =
            mockMvc
                .perform(
                    post(REFRESH)
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("{}"),
                ).expectEnvelope(415, 90008)
                .andReturn()
                .response
                .contentAsString

        listOf("com.aechak", "org.springframework", "Content-Type", "text/plain").forEach {
            assert(!body.contains(it)) { "내부 구현이 응답에 실렸다($it): $body" }
        }
    }

    @Test
    @DisplayName("JSON을 받지 않겠다는 요청은 406과 90009이고 봉투는 JSON으로 나간다")
    fun unacceptableAcceptReturnsNotAcceptable() {
        mockMvc
            .perform(get(PRODUCTS).header(HttpHeaders.ACCEPT, MediaType.APPLICATION_XML_VALUE))
            .expectEnvelope(406, 90009)
    }

    @Test
    @DisplayName("Accept 문법이 깨진 요청도 406 봉투로 나간다")
    fun unparsableAcceptReturnsNotAcceptable() {
        mockMvc.perform(get(PRODUCTS).header(HttpHeaders.ACCEPT, "///")).expectEnvelope(406, 90009)
    }

    @Test
    @DisplayName("없는 상품의 404 봉투는 Accept가 적대적이어도 그대로 404다")
    fun notFoundEnvelopeSurvivesHostileAccept() {
        mockMvc
            .perform(get("$PRODUCTS/no-such-product").header(HttpHeaders.ACCEPT, MediaType.APPLICATION_XML_VALUE))
            .expectEnvelope(404, 40000)
    }

    @Test
    @DisplayName("파라미터 검증 400 봉투도 Accept가 적대적이어도 그대로 400이다")
    fun validationEnvelopeSurvivesHostileAccept() {
        mockMvc
            .perform(get("$PRODUCTS?size=0").header(HttpHeaders.ACCEPT, MediaType.APPLICATION_XML_VALUE))
            .expectEnvelope(400, 90001)
    }

    @Test
    @DisplayName("Content-Type과 Accept가 동시에 어긋나도 415 봉투가 나간다")
    fun unsupportedContentTypeSurvivesHostileAccept() {
        mockMvc
            .perform(
                post(REFRESH)
                    .contentType(MediaType.TEXT_PLAIN)
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_XML_VALUE)
                    .content("{}"),
            ).expectEnvelope(415, 90008)
    }

    /**
     * 매핑 없는 경로·메서드의 404·405도 같은 못박기를 지난다.
     *
     * 이 둘은 전역 핸들러에서 봉투 헬퍼를 거치지 않고 ResponseEntity를 직접 만들던 자리였다 —
     * 그 상태로는 적대적 Accept에서 봉투를 쓸 수 없어 404·405가 통째로 사라진다
     * (실 컨테이너에서는 그 자리에 401이 들어선다). 이 경로만 토큰을 쓰는 이유는
     * 매핑이 없는 요청·허용되지 않은 메서드는 시큐리티가 먼저 인증을 요구하기 때문이다.
     */
    @Test
    @DisplayName("매핑 없는 경로의 404 봉투도 Accept가 적대적이어도 그대로 404다")
    fun unmappedPathEnvelopeSurvivesHostileAccept() {
        mockMvc
            .perform(
                get("/api/v1/no-such-path")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer ${mintAccessToken(createActiveUser())}")
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_XML_VALUE),
            ).expectEnvelope(404, 90006)
    }

    @Test
    @DisplayName("허용되지 않은 메서드의 405 봉투도 적대적 Accept를 견디고 Allow 헤더를 지킨다")
    fun methodNotAllowedEnvelopeSurvivesHostileAccept() {
        mockMvc
            .perform(
                delete(PRODUCTS)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer ${mintAccessToken(createActiveUser())}")
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_XML_VALUE),
            ).expectEnvelope(405, 90007)
            // 못박기를 넣으면서 Allow를 떨어뜨리지 않았는지 — 클라이언트가 무엇을 쓸지 아는 유일한 단서다.
            .andExpect(header().string(HttpHeaders.ALLOW, containsString("GET")))
    }

    @Test
    @DisplayName("JSON을 받는 정상 요청은 토큰 없이도 그대로 200이다")
    fun acceptableRequestStillSucceeds() {
        mockMvc
            .perform(get("$PRODUCTS?size=1").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
    }

    /** 상태코드·errorCode·JSON Content-Type을 한 번에 본다. Content-Type 단언이 "협상에 맡기지 않았는지"를 고정한다. */
    private fun ResultActions.expectEnvelope(
        expectedStatus: Int,
        expectedErrorCode: Int,
    ): ResultActions =
        andExpect(status().`is`(expectedStatus))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.errorCode").value(expectedErrorCode))
            .andExpect(jsonPath("$.message").isNotEmpty())

    companion object {
        private const val REFRESH = "/api/v1/auth/refresh"
        private const val PRODUCTS = "/api/v1/products"
    }
}
