package com.aechak.seller.seller

import com.aechak.api.support.IntegrationTestBase
import com.aechak.domain.order.shipment.CourierMaster
import com.aechak.domain.seller.seller.Seller
import com.aechak.domain.seller.seller.enums.SellerStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.web.FilterChainProxy
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

/**
 * 셀러 배송 정책 조회·저장 API 통합 계약. HTTP 경계부터 실 MySQL까지 태워 응답 모양과 거절 코드를 고정한다.
 * 깨지면 셀러센터 배송 정책 화면이 읽는 필드가 바뀌었거나, 막아야 할 값을 저장시킨 것이다.
 */
class ShippingPolicyIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var context: WebApplicationContext

    @Autowired
    private lateinit var securityFilterChain: FilterChainProxy

    private lateinit var mockMvc: MockMvc
    private var sellerUserId = 0L
    private lateinit var token: String

    @BeforeEach
    fun setUp() {
        mockMvc =
            MockMvcBuilders
                .webAppContextSetup(context)
                .addFilters<DefaultMockMvcBuilder>(securityFilterChain)
                .build()
        sellerUserId = createActiveUser()
        token = mintAccessToken(sellerUserId)
        openSeller(sellerUserId)
        seedCourier("CJ", "CJ대한통운")
    }

    @Test
    fun `배송 정책을 설정하지 않은 셀러는 컬럼 기본값을 받는다`() {
        mockMvc
            .perform(getRequest(token))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.defaultCourierCode").value(null))
            .andExpect(jsonPath("$.data.baseShippingFee").value(3000))
            .andExpect(jsonPath("$.data.freeShippingThreshold").value(null))
            .andExpect(jsonPath("$.data.returnSameAsOrigin").value(true))
            .andExpect(jsonPath("$.data.jejuExtraFee").value(0))
            .andExpect(jsonPath("$.data.remoteAreaExtraFee").value(0))
            .andExpect(jsonPath("$.data.returnFee").value(0))
            .andExpect(jsonPath("$.data.exchangeFee").value(0))
            .andExpect(jsonPath("$.data.avgDispatchDays").value(2))
    }

    @Test
    fun `저장한 배송 정책이 그대로 다시 조회된다`() {
        mockMvc.perform(putRequest(token, policyJson())).andExpect(status().isNoContent)

        mockMvc
            .perform(getRequest(token))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.defaultCourierCode").value("CJ"))
            .andExpect(jsonPath("$.data.baseShippingFee").value(4000))
            .andExpect(jsonPath("$.data.freeShippingThreshold").value(50000))
            .andExpect(jsonPath("$.data.originZipCode").value("06236"))
            .andExpect(jsonPath("$.data.originRoadAddress").value("서울 강남구 테헤란로 1"))
            .andExpect(jsonPath("$.data.originDetailAddress").value("3층"))
            .andExpect(jsonPath("$.data.returnSameAsOrigin").value(false))
            .andExpect(jsonPath("$.data.returnZipCode").value("13529"))
            .andExpect(jsonPath("$.data.returnRoadAddress").value("경기 성남시 판교역로 2"))
            .andExpect(jsonPath("$.data.returnDetailAddress").value("물류센터"))
            .andExpect(jsonPath("$.data.jejuExtraFee").value(3000))
            .andExpect(jsonPath("$.data.remoteAreaExtraFee").value(5000))
            .andExpect(jsonPath("$.data.returnFee").value(5000))
            .andExpect(jsonPath("$.data.exchangeFee").value(10000))
            .andExpect(jsonPath("$.data.avgDispatchDays").value(3))
            .andExpect(jsonPath("$.data.shippingPolicyText").value("오후 2시 이전 결제분은 당일 출고합니다."))
    }

    @Test
    fun `택배사를 비워 두고 저장할 수 있다`() {
        mockMvc
            .perform(putRequest(token, policyJson(defaultCourierCode = null)))
            .andExpect(status().isNoContent)

        mockMvc
            .perform(getRequest(token))
            .andExpect(jsonPath("$.data.defaultCourierCode").value(null))
    }

    @Test
    fun `반품지가 출고지와 같으면 반품지 3필드는 저장되지 않는다`() {
        mockMvc
            .perform(putRequest(token, policyJson(returnSameAsOrigin = true)))
            .andExpect(status().isNoContent)

        mockMvc
            .perform(getRequest(token))
            .andExpect(jsonPath("$.data.returnSameAsOrigin").value(true))
            .andExpect(jsonPath("$.data.returnZipCode").value(null))
            .andExpect(jsonPath("$.data.returnRoadAddress").value(null))
            .andExpect(jsonPath("$.data.returnDetailAddress").value(null))

        val seller = loadSeller(sellerUserId)
        assertNull(seller.returnZipCode, "출고지와 같다고 했는데 반품지가 남으면 안 된다")
    }

    @Test
    fun `무료배송 기준이 기본 배송비보다 낮으면 400과 10005다`() {
        mockMvc
            .perform(putRequest(token, policyJson(baseShippingFee = 4000, freeShippingThreshold = 3000)))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(10005))

        assertEquals(3000L, loadSeller(sellerUserId).baseShippingFee, "거절된 요청이 값을 바꾸면 안 된다")
    }

    @Test
    fun `기본 배송비가 음수면 400과 10005다`() {
        mockMvc
            .perform(putRequest(token, policyJson(baseShippingFee = -1, freeShippingThreshold = null)))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(10005))
    }

    @Test
    fun `반품비가 음수면 400과 10005다`() {
        mockMvc
            .perform(putRequest(token, policyJson(returnFee = -1)))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(10005))
    }

    @Test
    fun `평균 발송 소요일이 허용 범위를 벗어나면 400과 10005다`() {
        mockMvc
            .perform(putRequest(token, policyJson(avgDispatchDays = 0)))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(10005))

        mockMvc
            .perform(putRequest(token, policyJson(avgDispatchDays = 8)))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(10005))
    }

    @Test
    fun `기본 배송비가 상한을 넘으면 400과 10005다`() {
        mockMvc
            .perform(
                putRequest(
                    token,
                    policyJson(baseShippingFee = Seller.MAX_SHIPPING_FEE + 1, freeShippingThreshold = null),
                ),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(10005))

        assertEquals(3000L, loadSeller(sellerUserId).baseShippingFee, "거절된 요청이 값을 바꾸면 안 된다")
    }

    /**
     * 셀러별 배송비는 주문 금액 계산에서 합산되므로 Long 상한에 가까운 값을 저장시키면
     * 합이 되돌아가 터무니없이 싼 주문이 만들어진다. 상한 검증이 사라지면 이 테스트가 먼저 깨진다.
     */
    @Test
    fun `Long 상한에 가까운 배송비는 저장되지 않는다`() {
        mockMvc
            .perform(
                putRequest(
                    token,
                    policyJson(baseShippingFee = 6_148_914_691_236_517_539L, freeShippingThreshold = null),
                ),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(10005))

        assertEquals(3000L, loadSeller(sellerUserId).baseShippingFee)
    }

    @Test
    fun `반품비가 상한을 넘으면 400과 10005다`() {
        mockMvc
            .perform(putRequest(token, policyJson(returnFee = Seller.MAX_SHIPPING_FEE + 1)))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(10005))
    }

    @Test
    fun `무료배송 기준이 상한을 넘으면 400과 10005다`() {
        mockMvc
            .perform(putRequest(token, policyJson(freeShippingThreshold = Seller.MAX_FREE_SHIPPING_THRESHOLD + 1)))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(10005))
    }

    @Test
    fun `배송 정책 안내가 허용 길이를 넘으면 400과 90001이다`() {
        val tooLong = "가".repeat(Seller.MAX_SHIPPING_POLICY_TEXT_LENGTH + 1)

        mockMvc
            .perform(putRequest(token, policyJson().replace("오후 2시 이전 결제분은 당일 출고합니다.", tooLong)))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value(90001))

        assertNull(loadSeller(sellerUserId).shippingPolicyText, "거절된 요청이 값을 바꾸면 안 된다")
    }

    @Test
    fun `없는 택배사로 저장하면 404와 50303이다`() {
        mockMvc
            .perform(putRequest(token, policyJson(defaultCourierCode = "NOPE")))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.errorCode").value(50303))

        assertNull(loadSeller(sellerUserId).defaultCourierCode, "거절된 요청이 값을 바꾸면 안 된다")
    }

    @Test
    fun `비활성 택배사로 저장하면 404와 50303이다`() {
        seedCourier("LOTTE", "롯데택배", isActive = false)

        mockMvc
            .perform(putRequest(token, policyJson(defaultCourierCode = "LOTTE")))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.errorCode").value(50303))
    }

    @Test
    fun `정지된 셀러의 조회와 저장은 403과 10002다`() {
        suspendSeller(sellerUserId)

        mockMvc
            .perform(getRequest(token))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.errorCode").value(10002))

        mockMvc
            .perform(putRequest(token, policyJson()))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.errorCode").value(10002))
    }

    @Test
    fun `셀러가 아닌 계정의 조회와 저장은 404와 10000이다`() {
        val notSeller = mintAccessToken(createActiveUser())

        mockMvc
            .perform(getRequest(notSeller))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.errorCode").value(10000))

        mockMvc
            .perform(putRequest(notSeller, policyJson()))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.errorCode").value(10000))
    }

    @Test
    fun `타 셀러의 저장은 내 배송 정책을 바꾸지 않는다`() {
        val otherSellerId = createActiveUser()
        openSeller(otherSellerId)

        mockMvc
            .perform(putRequest(mintAccessToken(otherSellerId), policyJson(baseShippingFee = 9000, freeShippingThreshold = 90000)))
            .andExpect(status().isNoContent)

        mockMvc
            .perform(getRequest(token))
            .andExpect(jsonPath("$.data.baseShippingFee").value(3000))
            .andExpect(jsonPath("$.data.freeShippingThreshold").value(null))

        assertEquals(9000L, loadSeller(otherSellerId).baseShippingFee)
    }

    @Test
    fun `미인증 조회와 저장은 401과 20004다`() {
        mockMvc
            .perform(get("/api/v1/sellers/me/shipping-policy"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.errorCode").value(20004))

        mockMvc
            .perform(
                put("/api/v1/sellers/me/shipping-policy")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(policyJson()),
            ).andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.errorCode").value(20004))
    }

    // ---------- 헬퍼 ----------

    private fun getRequest(token: String): MockHttpServletRequestBuilder =
        get("/api/v1/sellers/me/shipping-policy").header(HttpHeaders.AUTHORIZATION, "Bearer $token")

    private fun putRequest(
        token: String,
        body: String,
    ): MockHttpServletRequestBuilder =
        put("/api/v1/sellers/me/shipping-policy")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body)

    private fun policyJson(
        defaultCourierCode: String? = "CJ",
        baseShippingFee: Long = 4000,
        freeShippingThreshold: Long? = 50_000,
        returnSameAsOrigin: Boolean = false,
        jejuExtraFee: Long = 3000,
        remoteAreaExtraFee: Long = 5000,
        returnFee: Long = 5000,
        exchangeFee: Long = 10_000,
        avgDispatchDays: Int = 3,
    ): String =
        """
        {"defaultCourierCode": ${defaultCourierCode?.let { "\"$it\"" }},
         "baseShippingFee": $baseShippingFee, "freeShippingThreshold": $freeShippingThreshold,
         "originZipCode": "06236", "originRoadAddress": "서울 강남구 테헤란로 1", "originDetailAddress": "3층",
         "returnSameAsOrigin": $returnSameAsOrigin,
         "returnZipCode": "13529", "returnRoadAddress": "경기 성남시 판교역로 2", "returnDetailAddress": "물류센터",
         "jejuExtraFee": $jejuExtraFee, "remoteAreaExtraFee": $remoteAreaExtraFee,
         "returnFee": $returnFee, "exchangeFee": $exchangeFee, "avgDispatchDays": $avgDispatchDays,
         "shippingPolicyText": "오후 2시 이전 결제분은 당일 출고합니다."}
        """.trimIndent()

    private fun openSeller(userId: Long) {
        tx.execute { em.persist(Seller.open(userId, "애착상회$userId", 3000L)) }
    }

    private fun seedCourier(
        code: String,
        name: String,
        isActive: Boolean = true,
    ) {
        tx.execute { em.persist(CourierMaster.register(code, name, isActive)) }
    }

    private fun suspendSeller(userId: Long) {
        tx.execute {
            em
                .createQuery("update Seller s set s.status = :status where s.userId = :userId")
                .setParameter("status", SellerStatus.SUSPENDED)
                .setParameter("userId", userId)
                .executeUpdate()
        }
    }

    /** 1차 캐시 가짜 통과를 막으려고 새 트랜잭션에서 다시 읽는다. */
    private fun loadSeller(userId: Long): Seller = tx.execute { em.find(Seller::class.java, userId) }!!
}
