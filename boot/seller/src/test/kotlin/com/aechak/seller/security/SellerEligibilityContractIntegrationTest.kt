package com.aechak.seller.security

import com.aechak.api.support.IntegrationTestBase
import com.aechak.application.auth.error.AuthErrorCode
import com.aechak.common.error.ErrorCode
import com.aechak.domain.order.error.OrderErrorCode
import com.aechak.domain.product.error.ProductErrorCode
import com.aechak.domain.seller.error.SellerErrorCode
import com.aechak.domain.seller.seller.Seller
import com.aechak.domain.seller.seller.enums.SellerStatus
import com.aechak.domain.settlement.error.SettlementErrorCode
import com.jayway.jsonpath.JsonPath
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.web.FilterChainProxy
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping

/**
 * 셀러센터 전 경로의 자격 응답 계약. 셀러로 등록된 적이 없는 계정에 같은 상태·같은 에러코드가 가는지,
 * 그리고 그것이 정지·탈퇴 셀러의 거절과 구별되는지를 고정한다.
 *
 * 경로 목록을 손으로 적지 않고 띄운 핸들러 매핑에서 뽑아 대조한다 — 경로가 늘었는데 아래 표에
 * 등록되지 않으면 테스트가 먼저 깨진다. 표만 들고 돌면 새 경로가 조용히 빠진다.
 */
class SellerEligibilityContractIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var context: WebApplicationContext

    @Autowired
    private lateinit var securityFilterChain: FilterChainProxy

    private lateinit var mockMvc: MockMvc
    private lateinit var notSellerToken: String

    @BeforeEach
    fun setUp() {
        mockMvc =
            MockMvcBuilders
                .webAppContextSetup(context)
                .addFilters<DefaultMockMvcBuilder>(securityFilterChain)
                .build()
        notSellerToken = mintAccessToken(createActiveUser())
    }

    @Test
    fun `셀러센터 경로가 하나라도 자격 계약에 등록되지 않으면 실패한다`() {
        val declared = GATED.keys + EXEMPT
        val discovered = discoveredOperations()

        assertTrue(
            (discovered - declared).isEmpty(),
            "새 셀러센터 경로가 자격 계약에 등록되지 않았다 — GATED나 EXEMPT에 넣어라: ${(discovered - declared).sorted()}",
        )
        assertTrue(
            (declared - discovered).isEmpty(),
            "표에만 있고 실제로는 없는 경로다 — 경로가 바뀌면 커버리지가 조용히 사라진다: ${(declared - discovered).sorted()}",
        )
    }

    @Test
    fun `셀러가 아닌 계정은 셀러센터 전 경로에서 403과 10004를 받는다`() {
        assertTrue(GATED.isNotEmpty(), "자격 게이트 대상이 0건이면 이 테스트는 아무것도 지키지 못한다")

        GATED.forEach { (operation, body) ->
            val result = mockMvc.perform(requestOf(operation, body, notSellerToken)).andReturn()

            assertEquals(403, result.response.status, "$operation — ${bodyOf(result)}")
            assertEquals(SellerErrorCode.NOT_SELLER.code, errorCodeOf(result), "$operation — ${bodyOf(result)}")
        }
    }

    /** 403과 401을 가르는 쪽도 함께 못 박는다 — 프런트가 "로그인을 다시 시킬지"를 이걸로 가른다. */
    @Test
    fun `토큰이 없으면 셀러센터 전 경로가 401과 20004다`() {
        GATED.forEach { (operation, body) ->
            val result = mockMvc.perform(requestOf(operation, body, token = null)).andReturn()

            assertEquals(401, result.response.status, "$operation — ${bodyOf(result)}")
            assertEquals(AuthErrorCode.UNAUTHENTICATED.code, errorCodeOf(result), "$operation — ${bodyOf(result)}")
        }
    }

    /** 한 화면이 입점 신청과 스토어 운영을 겸하는 자리라 조회는 거절하지 않는다. 이 모양이 올바른 기준이었다. */
    @Test
    fun `셀러가 아닌 계정의 스토어 설정 조회는 200으로 입점 단계를 돌려준다`() {
        mockMvc
            .perform(get(STORE_PATH).header(HttpHeaders.AUTHORIZATION, "Bearer $notSellerToken"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.isSeller").value(false))
            .andExpect(jsonPath("$.data.applicationStatus").value(null))
            .andExpect(jsonPath("$.data.store").value(null))
    }

    /**
     * 정지 셀러의 거절은 바뀌지 않는다 — 그 경우에는 "탈퇴하거나 정지된" 메시지가 사실이다.
     * 이 테스트가 깨지면 비셀러를 고치면서 제재 셀러의 응답까지 건드린 것이다.
     */
    @Test
    fun `정지 셀러는 셀러가 아닌 계정과 다른 코드를 받는다`() {
        val suspendedUserId = createActiveUser()
        openSeller(suspendedUserId)
        suspendSeller(suspendedUserId)
        val suspended = mintAccessToken(suspendedUserId)

        SUSPENDED_CODES.forEach { (operation, expected) ->
            val result = mockMvc.perform(requestOf(operation, GATED.getValue(operation), suspended)).andReturn()

            assertEquals(403, result.response.status, "$operation — ${bodyOf(result)}")
            assertEquals(expected.code, errorCodeOf(result), "$operation — ${bodyOf(result)}")
            assertTrue(
                errorCodeOf(result) != SellerErrorCode.NOT_SELLER.code,
                "$operation 이 제재 셀러에게도 비셀러 코드를 보낸다 — 두 상태가 합쳐졌다",
            )
        }
    }

    // ---------- 헬퍼 ----------

    /** 띄운 컨테이너의 핸들러 매핑에서 "VERB 경로"를 전부 모은다. */
    private fun discoveredOperations(): Set<String> =
        context
            .getBeansOfType(RequestMappingHandlerMapping::class.java)
            .values
            .flatMap { it.handlerMethods.entries }
            .filter { (_, handler) -> handler.beanType.packageName.startsWith(BASE_PACKAGE) }
            .flatMap { (info, _) ->
                // 메서드를 선언하지 않은 핸들러는 ANY로 잡는다 — 표에 없는 이름이라 등록 검사가 먼저 깨진다
                val verbs =
                    info.methodsCondition.methods
                        .map { it.name }
                        .ifEmpty { listOf("ANY") }
                info.pathPatternsCondition
                    ?.patternValues
                    .orEmpty()
                    .flatMap { path -> verbs.map { "$it $path" } }
            }.toSet()

    private fun requestOf(
        operation: String,
        body: String?,
        token: String?,
    ): MockHttpServletRequestBuilder {
        val (verb, pattern) = operation.split(" ", limit = 2)
        val url = pattern.replace(PATH_VARIABLE, "1")
        val builder =
            when (verb) {
                "GET" -> get(url)
                "POST" -> post(url)
                "PUT" -> put(url)
                "PATCH" -> patch(url)
                "DELETE" -> delete(url)
                else -> error("$verb 는 이 테스트가 만들 줄 모르는 메서드다")
            }
        token?.let { builder.header(HttpHeaders.AUTHORIZATION, "Bearer $it") }
        body?.let { builder.contentType(MediaType.APPLICATION_JSON).content(it) }
        return builder
    }

    private fun bodyOf(result: MvcResult): String = result.response.contentAsString

    private fun errorCodeOf(result: MvcResult): Int = JsonPath.read(bodyOf(result), "$.errorCode")

    private fun openSeller(userId: Long) {
        tx.execute { em.persist(Seller.open(userId, "애착상회$userId", 3000L)) }
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

    private companion object {
        const val BASE_PACKAGE = "com.aechak"
        const val STORE_PATH = "/api/v1/sellers/me/store"
        val PATH_VARIABLE = Regex("\\{[^/]+}")

        const val SHIPPING_POLICY_BODY =
            """
            {"defaultCourierCode": null, "baseShippingFee": 3000, "freeShippingThreshold": null,
             "originZipCode": "13529", "originRoadAddress": "경기 성남시 판교역로 2", "originDetailAddress": "물류센터",
             "returnSameAsOrigin": true, "returnZipCode": null, "returnRoadAddress": null, "returnDetailAddress": null,
             "jejuExtraFee": 0, "remoteAreaExtraFee": 0, "returnFee": 0, "exchangeFee": 0,
             "avgDispatchDays": 2, "shippingPolicyText": null}
            """

        const val STORE_BODY =
            """
            {"storeName": "애착상회", "brandIntro": null, "profileImageKey": null, "bannerImageKey": null,
             "zipCode": null, "roadAddress": null, "detailAddress": null}
            """

        const val PRODUCT_BODY =
            """
            {"categoryId": 1, "productName": "자격 게이트 확인용", "regularPrice": 10000,
             "thumbnailImageKey": "products/1/thumb.jpg",
             "optionCombinations": [{"optionValues": [], "additionalPrice": 0, "stockQuantity": 1}]}
            """

        const val PRODUCT_UPDATE_BODY =
            """
            {"categoryId": 1, "productName": "자격 게이트 확인용", "regularPrice": 10000,
             "thumbnailImageKey": "products/1/thumb.jpg"}
            """

        const val INVOICE_BODY = """{"courierCode": "CJ", "trackingNumber": "1234567890"}"""

        const val REPLY_BODY = """{"content": "자격 게이트 확인용 답글"}"""

        /**
         * 셀러 자격을 요구하는 경로 전수. 값은 그 경로가 자격 게이트까지 도달하는 데 필요한 본문이다 —
         * 본문 검증이 게이트보다 앞서서, 본문이 틀리면 400이 되어 자격 계약을 재지 못한다.
         */
        val GATED: Map<String, String?> =
            linkedMapOf(
                "GET /api/v1/sellers/me/shipping-policy" to null,
                "PUT /api/v1/sellers/me/shipping-policy" to SHIPPING_POLICY_BODY,
                "PUT /api/v1/sellers/me/store" to STORE_BODY,
                "GET /api/v1/sellers/me/dashboard/summary" to null,
                "GET /api/v1/sellers/me/products" to null,
                "POST /api/v1/sellers/me/products" to PRODUCT_BODY,
                "GET /api/v1/sellers/me/products/{productId}" to null,
                "PUT /api/v1/sellers/me/products/{productId}" to PRODUCT_UPDATE_BODY,
                "PATCH /api/v1/sellers/me/products/{productId}/status" to """{"saleStatus": "ON_SALE"}""",
                "GET /api/v1/sellers/me/products/{productId}/options" to null,
                "PATCH /api/v1/sellers/me/products/{productId}/options/{combinationId}" to """{"stockDelta": 1}""",
                "GET /api/v1/sellers/me/products/{productId}/inspection" to null,
                "POST /api/v1/sellers/me/products/{productId}/inspection/appeals" to """{"reason": "확인 요청"}""",
                "GET /api/v1/sellers/me/product-drafts" to null,
                "POST /api/v1/sellers/me/product-drafts" to "{}",
                "GET /api/v1/sellers/me/product-drafts/{draftId}" to null,
                "PUT /api/v1/sellers/me/product-drafts/{draftId}" to "{}",
                "DELETE /api/v1/sellers/me/product-drafts/{draftId}" to null,
                "POST /api/v1/sellers/me/product-drafts/autofill" to """{"productName": "자격 게이트 확인용 상품"}""",
                "GET /api/v1/sellers/me/orders" to null,
                "GET /api/v1/sellers/me/orders/{orderPublicId}" to null,
                "POST /api/v1/sellers/me/orders/{orderPublicId}/accept" to null,
                "POST /api/v1/sellers/me/orders/{orderPublicId}/delivery-complete" to null,
                "POST /api/v1/sellers/me/orders/{orderPublicId}/shipment" to INVOICE_BODY,
                "GET /api/v1/sellers/me/claims" to null,
                "GET /api/v1/sellers/me/claims/{claimPublicId}" to null,
                "POST /api/v1/sellers/me/claims/{claimPublicId}/approve" to "{}",
                "POST /api/v1/sellers/me/claims/{claimPublicId}/reject" to "{}",
                "PATCH /api/v1/sellers/me/claims/{claimPublicId}/memo" to "{}",
                "POST /api/v1/sellers/me/claims/{claimPublicId}/collection" to INVOICE_BODY,
                "POST /api/v1/sellers/me/claims/{claimPublicId}/collection/complete" to null,
                "POST /api/v1/sellers/me/claims/{claimPublicId}/reship" to INVOICE_BODY,
                "GET /api/v1/sellers/me/settlements" to null,
                "GET /api/v1/sellers/me/settlements/months" to null,
                "GET /api/v1/sellers/me/settlements/{orderPublicId}" to null,
                "GET /api/v1/sellers/me/reviews" to null,
                "POST /api/v1/sellers/me/reviews/{reviewId}/reply" to REPLY_BODY,
                "PUT /api/v1/sellers/me/reviews/{reviewId}/reply" to REPLY_BODY,
                "DELETE /api/v1/sellers/me/reviews/{reviewId}/reply" to null,
            )

        /** 자격 게이트를 두지 않는 경로와 그 이유. */
        val EXEMPT =
            setOf(
                // 입점 신청은 셀러가 아닌 계정이 쓰는 경로다
                "POST /api/v1/seller-applications",
                "POST /api/v1/seller-applications/submit",
                "GET /api/v1/seller-applications",
                // 한 화면이 입점 신청과 스토어 운영을 겸해 조회는 200으로 단계를 돌려준다
                "GET $STORE_PATH",
                // 택배사 마스터는 셀러별 자원이 아니다
                "GET /api/v1/sellers/couriers",
            )

        /** 정지 셀러가 받는 코드 — 이 PR이 건드리지 않는 쪽이다. 티켓이 실측한 경로를 그대로 쓴다. */
        val SUSPENDED_CODES: Map<String, ErrorCode> =
            linkedMapOf(
                "GET /api/v1/sellers/me/shipping-policy" to SellerErrorCode.SELLER_NOT_ACTIVE,
                "GET /api/v1/sellers/me/products" to ProductErrorCode.PRODUCT_SELLER_READ_FORBIDDEN,
                "GET /api/v1/sellers/me/product-drafts" to ProductErrorCode.PRODUCT_SELLER_NOT_ACTIVE,
                "GET /api/v1/sellers/me/settlements" to SettlementErrorCode.SETTLEMENT_ACCESS_DENIED,
                "GET /api/v1/sellers/me/orders" to OrderErrorCode.ORDER_ACCESS_DENIED,
                "GET /api/v1/sellers/me/dashboard/summary" to SellerErrorCode.SELLER_CENTER_READ_FORBIDDEN,
                "GET /api/v1/sellers/me/claims" to OrderErrorCode.CLAIM_ACCESS_DENIED,
            )
    }
}
