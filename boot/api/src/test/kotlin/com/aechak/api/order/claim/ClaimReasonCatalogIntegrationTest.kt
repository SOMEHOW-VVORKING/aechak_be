package com.aechak.api.order.claim

import com.aechak.api.support.IntegrationTestBase
import com.aechak.domain.order.claim.enums.ClaimReasonCode
import com.aechak.domain.order.claim.enums.ClaimType
import com.jayway.jsonpath.JsonPath
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.security.web.FilterChainProxy
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

/**
 * 선택 가능한 사유를 내려주는 경로. 프런트가 코드·문구·상세 필수 여부를 하드코딩하지 않아야 하므로
 * 이 응답이 서버 어휘와 한 글자도 어긋나지 않는지 본다 — 어긋나면 화면 선택지가 서버 거절과 갈린다.
 */
class ClaimReasonCatalogIntegrationTest : IntegrationTestBase() {
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
    fun `유형별 선택지를 서버 어휘와 같은 순서로 내린다`() {
        val body =
            mockMvc
                .perform(get("/api/v1/claims/reasons").bearer(token))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.claimTypes.length()").value(ClaimType.entries.size))
                .andReturn()
                .response
                .getContentAsString(Charsets.UTF_8)

        assertEquals(ClaimType.entries.map { it.name }, JsonPath.read<List<String>>(body, "$.data.claimTypes[*].claimType"))
        ClaimType.entries.forEachIndexed { index, claimType ->
            val expected = ClaimReasonCode.selectableFor(claimType)
            assertEquals(
                expected.map { it.name },
                JsonPath.read<List<String>>(body, "$.data.claimTypes[$index].reasons[*].code"),
                "$claimType 선택지",
            )
            assertEquals(
                expected.map { it.label },
                JsonPath.read<List<String>>(body, "$.data.claimTypes[$index].reasons[*].label"),
                "$claimType 화면 문구",
            )
            assertEquals(
                expected.map { it.requiresDetail() },
                JsonPath.read<List<Boolean>>(body, "$.data.claimTypes[$index].reasons[*].detailRequired"),
                "$claimType 상세 필수 여부",
            )
        }
    }

    @Test
    fun `교환에는 가격 조건이 없고 취소에는 불량이 없다`() {
        val exchange = ClaimType.entries.indexOf(ClaimType.EXCHANGE)
        val cancel = ClaimType.entries.indexOf(ClaimType.CANCEL)

        val body =
            mockMvc
                .perform(get("/api/v1/claims/reasons").bearer(token))
                .andExpect(status().isOk)
                .andReturn()
                .response
                .getContentAsString(Charsets.UTF_8)

        assertEquals(
            listOf("DEFECTIVE", "WRONG_ITEM", "WRONG_OPTION", "OTHER"),
            JsonPath.read<List<String>>(body, "$.data.claimTypes[$exchange].reasons[*].code"),
        )
        assertEquals(
            listOf("CHANGE_OF_MIND", "WRONG_OPTION", "PRICE_MISMATCH", "DELIVERY_DELAY", "OTHER"),
            JsonPath.read<List<String>>(body, "$.data.claimTypes[$cancel].reasons[*].code"),
        )
    }

    private fun MockHttpServletRequestBuilder.bearer(token: String): MockHttpServletRequestBuilder =
        this.header(HttpHeaders.AUTHORIZATION, "Bearer $token")
}
