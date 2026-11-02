package com.aechak.seller.product

import com.aechak.api.support.IntegrationTestBase
import com.aechak.application.product.draft.port.ProductDraftAutofillPort
import com.aechak.application.product.draft.port.ProductDraftAutofillRequest
import com.aechak.application.product.draft.port.ProductDraftAutofillResult
import com.aechak.domain.seller.seller.Seller
import com.aechak.domain.seller.seller.enums.SellerStatus
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.web.FilterChainProxy
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * [통합] 셀러가 초안을 받는 경로 하나를 고정한다.
 *
 * 지키는 것이 셋이다. ① AI 가 돌려준 필드별 사유(fieldStatus·basis·sellerNotice)가 깎이지 않고
 * 화면까지 간다 — 깎이면 빈 폼의 이유를 셀러에게 말할 수 없다. ② 운영 축(모델 비용·가드 탈락·
 * 품목 신호)은 셀러 응답에 **없다**. ③ 초안 실패가 5xx 가 아니다 — 보조 기능의 실패로 등록
 * 화면에 전역 에러 토스트가 뜨면 등록 자체가 깨진 것처럼 보인다.
 *
 * 실 어댑터는 base-url 이 비어 조립되지 않으므로(AiDraftClientEnabled) 스텁 포트에 @Primary 가
 * 필요 없다.
 */
class SellerProductDraftAutofillIntegrationTest : IntegrationTestBase() {
    @TestConfiguration(proxyBeanMethods = false)
    class StubAutofillPortConfig {
        @Bean
        fun stubAutofillPort(): StubAutofillPort = StubAutofillPort()
    }

    class StubAutofillPort : ProductDraftAutofillPort {
        var next: ProductDraftAutofillResult = filled()
        var calls = 0
        var lastRequest: ProductDraftAutofillRequest? = null

        override fun generate(request: ProductDraftAutofillRequest): ProductDraftAutofillResult {
            calls++
            lastRequest = request
            return next
        }

        companion object {
            fun filled(): ProductDraftAutofillResult =
                ProductDraftAutofillResult(
                    state = ProductDraftAutofillResult.State.OK,
                    draft =
                        ProductDraftAutofillResult.Draft(
                            categoryId = 152L,
                            description = "모모의 하루 목줄입니다.",
                            optionGroups = listOf(ProductDraftAutofillResult.OptionGroup("사이즈", listOf("S", "M"))),
                            optionCombinations =
                                listOf(ProductDraftAutofillResult.OptionCombination(listOf("S"), 0L, null)),
                        ),
                    fieldStatus = mapOf("description" to "FILLED", "regularPrice" to "EMPTY_NOT_ATTEMPTED"),
                    basis = mapOf("description" to "NAME"),
                    sellerNotice = "가격과 재고는 직접 입력해 주세요.",
                )
        }
    }

    @Autowired
    private lateinit var context: WebApplicationContext

    @Autowired
    private lateinit var securityFilterChain: FilterChainProxy

    @Autowired
    private lateinit var stubPort: StubAutofillPort

    @Autowired
    private lateinit var redisTemplate: StringRedisTemplate

    private lateinit var mockMvc: MockMvc
    private var sellerUserId = 0L
    private lateinit var token: String

    /**
     * 쿨다운은 Redis 에 살고 DatabaseCleaner 는 그것을 지우지 않는다. 테이블을 비우면 user id 가
     * 1 부터 다시 채번되므로, 지우지 않으면 앞 테스트가 건 쿨다운이 다음 테스트의 첫 호출을 429 로
     * 막는다(PhoneVerificationUseCaseTest 와 같은 자리, 같은 처방).
     */
    @BeforeEach
    fun setUp() {
        val cooldownKeys = redisTemplate.keys("product:draft:autofill:cooldown:*")
        if (cooldownKeys.isNotEmpty()) {
            redisTemplate.delete(cooldownKeys)
        }
        mockMvc =
            MockMvcBuilders
                .webAppContextSetup(context)
                .addFilters<DefaultMockMvcBuilder>(securityFilterChain)
                .build()
        stubPort.next = StubAutofillPort.filled()
        stubPort.calls = 0
        sellerUserId = createActiveUser()
        token = mintAccessToken(sellerUserId)
        openSeller(sellerUserId)
    }

    @Test
    fun `초안을 받으면 네 필드와 필드별 사유가 오고 운영 축은 오지 않는다`() {
        mockMvc
            .perform(autofill(token, """{"productName": "모모의 하루 목줄 M", "categoryId": 152}"""))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.state").value("OK"))
            .andExpect(jsonPath("$.data.draft.categoryId").value(152))
            .andExpect(jsonPath("$.data.draft.description").value("모모의 하루 목줄입니다."))
            .andExpect(jsonPath("$.data.draft.optionGroups[0].name").value("사이즈"))
            .andExpect(jsonPath("$.data.draft.optionGroups[0].values[1]").value("M"))
            .andExpect(jsonPath("$.data.draft.optionCombinations[0].optionValues[0]").value("S"))
            .andExpect(jsonPath("$.data.draft.optionCombinations[0].stockQuantity").value(null))
            .andExpect(jsonPath("$.data.fieldStatus.description").value("FILLED"))
            .andExpect(jsonPath("$.data.fieldStatus.regularPrice").value("EMPTY_NOT_ATTEMPTED"))
            .andExpect(jsonPath("$.data.basis.description").value("NAME"))
            .andExpect(jsonPath("$.data.sellerNotice").value("가격과 재고는 직접 입력해 주세요."))
            // 모델 비용·가드 탈락·품목 신호는 셀러가 쓸 수 없는 운영 데이터다
            .andExpect(jsonPath("$.data.usage").doesNotExist())
            .andExpect(jsonPath("$.data.guardDrops").doesNotExist())
            .andExpect(jsonPath("$.data.itemSignal").doesNotExist())
            .andExpect(jsonPath("$.data.draft.regularPrice").doesNotExist())

        val sent = assertNotNull(stubPort.lastRequest)
        assertEquals("모모의 하루 목줄 M", sent.productName)
        assertEquals(152L, sent.categoryId)
    }

    @Test
    fun `셀러가 잠근 값은 그대로 AI 요청에 실린다`() {
        mockMvc
            .perform(
                autofill(
                    token,
                    """
                    {"productName": "모모의 하루 목줄 M",
                     "lockedFields": {"description": "직접 쓴 설명",
                                      "optionGroups": [{"name": "사이즈", "values": ["S", "M"]}]}}
                    """.trimIndent(),
                ),
            ).andExpect(status().isOk)

        val locked = assertNotNull(assertNotNull(stubPort.lastRequest).lockedFields)
        assertEquals("직접 쓴 설명", locked.description)
        assertEquals("사이즈", locked.optionGroups?.single()?.name)
        assertEquals(listOf("S", "M"), locked.optionGroups?.single()?.values)
    }

    @Test
    fun `AI 가 못 만들어도 200 이고 state 가 UNAVAILABLE 이다`() {
        stubPort.next = ProductDraftAutofillResult.unavailable()

        mockMvc
            .perform(autofill(token, """{"productName": "모모의 하루 목줄 M"}"""))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.state").value("UNAVAILABLE"))
            .andExpect(jsonPath("$.data.draft.description").value(null))
            .andExpect(jsonPath("$.data.fieldStatus").isEmpty)
    }

    @Test
    fun `연속으로 두 번 누르면 두 번째는 429 이고 모델을 부르지 않는다`() {
        mockMvc.perform(autofill(token, """{"productName": "모모의 하루 목줄 M"}""")).andExpect(status().isOk)

        mockMvc
            .perform(autofill(token, """{"productName": "모모의 하루 목줄 M"}"""))
            .andExpect(status().isTooManyRequests)
            .andExpect(jsonPath("$.errorCode").value(40402))

        assertEquals(1, stubPort.calls, "쿨다운이 모델 호출 앞을 막아야 돈이 두 번 나가지 않는다")
    }

    @Test
    fun `정지된 셀러는 403 이다`() {
        suspendSeller(sellerUserId)

        mockMvc
            .perform(autofill(token, """{"productName": "모모의 하루 목줄 M"}"""))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.errorCode").value(40004))

        assertEquals(0, stubPort.calls)
    }

    @Test
    fun `상품명이 비면 400 이다`() {
        mockMvc
            .perform(autofill(token, """{"productName": "   "}"""))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(90001))

        assertEquals(0, stubPort.calls, "AI 가 422 로 거절할 입력은 우리가 먼저 거른다")
    }

    private fun autofill(
        token: String,
        body: String,
    ) = post("/api/v1/sellers/me/product-drafts/autofill")
        .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
        .contentType(MediaType.APPLICATION_JSON)
        .content(body)

    private fun openSeller(userId: Long) {
        tx.execute { em.persist(Seller.open(userId, "애착상회", 0L)) }
    }

    private fun suspendSeller(userId: Long) {
        tx.execute {
            em
                .createQuery("update Seller s set s.status = :st where s.userId = :id")
                .setParameter("st", SellerStatus.SUSPENDED)
                .setParameter("id", userId)
                .executeUpdate()
        }
    }
}
