package com.aechak.infra.client.ai

import com.aechak.application.product.draft.port.ProductDraftAutofillRequest
import com.aechak.application.product.draft.port.ProductDraftAutofillResult
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withException
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import java.net.SocketTimeoutException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 계약. AI 초안 응답과 실패를 ProductDraftAutofillPort 어휘로 옮기는 규칙을 고정한다.
 *
 * 깨지면 셋 중 하나가 난다 — 초안 실패가 셀러의 등록 화면에 5xx로 새거나, AI가 "건너뜀"으로
 * 돌려준 사유(sellerNotice·fieldStatus)가 사라져 화면이 빈 폼의 이유를 말할 수 없게 되거나,
 * 공유 비밀 없이 요청이 나가 전건 403이 된다.
 */
class AiDraftClientAdapterTest {
    private val builder = RestClient.builder().baseUrl(BASE_URL)
    private val server = MockRestServiceServer.bindTo(builder).build()
    private val restClient = builder.build() // bindTo로 목 requestFactory를 꽂은 뒤에 만들어야 목이 먹음
    private val adapter = AiDraftClientAdapter(restClient, AiDraftProperties(baseUrl = BASE_URL, sharedSecret = SECRET))

    @Test
    fun `200 정상은 네 필드와 fieldStatus와 basis와 sellerNotice를 그대로 옮긴다`() {
        server
            .expect(requestTo("$BASE_URL/internal/seller-draft"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess(FILLED_RESPONSE_JSON, MediaType.APPLICATION_JSON))

        val result = adapter.generate(request())

        assertEquals(ProductDraftAutofillResult.State.OK, result.state)
        assertEquals(152L, result.draft.categoryId)
        assertEquals("모모의 하루 목줄입니다.", result.draft.description)
        assertEquals(
            listOf("S", "M"),
            result.draft.optionGroups
                .single()
                .values,
        )
        assertEquals(
            "사이즈",
            result.draft.optionGroups
                .single()
                .name,
        )
        assertEquals(
            listOf("S"),
            result.draft.optionCombinations
                .first()
                .optionValues,
        )
        assertEquals(
            0L,
            result.draft.optionCombinations
                .first()
                .additionalPrice,
        )
        assertNull(
            result.draft.optionCombinations
                .first()
                .stockQuantity,
            "재고는 AI가 채우지 않는다",
        )
        assertEquals("FILLED", result.fieldStatus["description"], "필드별 사유가 화면 분기의 축이다")
        assertEquals("EMPTY_NOT_ATTEMPTED", result.fieldStatus["regularPrice"])
        assertEquals("NAME", result.basis["description"], "무슨 근거로 채웠는지를 셀러가 본다")
        assertEquals("상품명이 길어 설명을 짧게 잡았습니다.", result.sellerNotice)
        server.verify()
    }

    /** AI는 모델을 못 불러도 200 + state=OK로 돌려준다. state를 "필드를 받았나"로 읽을 수 없다. */
    @Test
    fun `200 건너뜀은 state OK 그대로 올라가고 사유가 보존된다`() {
        server
            .expect(requestTo("$BASE_URL/internal/seller-draft"))
            .andRespond(withSuccess(SKIPPED_RESPONSE_JSON, MediaType.APPLICATION_JSON))

        val result = adapter.generate(request())

        assertEquals(ProductDraftAutofillResult.State.OK, result.state, "건너뜀을 UNAVAILABLE로 접으면 사유가 사라진다")
        assertNull(result.draft.description)
        assertTrue(result.draft.optionGroups.isEmpty())
        assertEquals("EMPTY_CALL_FAILED", result.fieldStatus["description"], "'다시 시도'와 '직접 작성'을 가르는 값이다")
        assertEquals("NONE", result.basis["description"])
        server.verify()
    }

    @Test
    fun `200 FAILED는 FAILED로 올라가고 fieldStatus가 보존된다`() {
        server
            .expect(requestTo("$BASE_URL/internal/seller-draft"))
            .andRespond(withSuccess(FAILED_RESPONSE_JSON, MediaType.APPLICATION_JSON))

        val result = adapter.generate(request())

        assertEquals(ProductDraftAutofillResult.State.FAILED, result.state)
        assertEquals("EMPTY_CALL_FAILED", result.fieldStatus["description"])
        server.verify()
    }

    /** AI FieldStatus가 개발 중 셋 늘었다. state도 늘 수 있고 그때 셀러가 500을 봐서는 안 된다. */
    @Test
    fun `모르는 state 문자열은 FAILED로 접고 터지지 않는다`() {
        server
            .expect(requestTo("$BASE_URL/internal/seller-draft"))
            .andRespond(withSuccess("""{"state":"PARTIAL","fieldStatus":{"description":"FILLED"}}""", MediaType.APPLICATION_JSON))

        val result = adapter.generate(request())

        assertEquals(ProductDraftAutofillResult.State.FAILED, result.state)
        server.verify()
    }

    @Test
    fun `403 공유 비밀 불일치는 UNAVAILABLE이다`() {
        assertUnavailable(withStatus(HttpStatus.FORBIDDEN).contentType(MediaType.APPLICATION_JSON).body("""{"error":"허용되지 않은 호출이다"}"""))
    }

    @Test
    fun `409 계약 버전 불일치는 UNAVAILABLE이다`() {
        assertUnavailable(withStatus(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_JSON).body("""{"error":"지원하지 않는 계약 버전이다"}"""))
    }

    @Test
    fun `503 기능 꺼짐은 UNAVAILABLE이다`() {
        assertUnavailable(
            withStatus(HttpStatus.SERVICE_UNAVAILABLE).contentType(MediaType.APPLICATION_JSON).body("""{"error":"초안 기능이 꺼져 있다"}"""),
        )
    }

    @Test
    fun `422 입력 거부는 UNAVAILABLE이다`() {
        assertUnavailable(
            withStatus(HttpStatus.UNPROCESSABLE_ENTITY).contentType(MediaType.APPLICATION_JSON).body("""{"error":"상품명이 비었다"}"""),
        )
    }

    @Test
    fun `읽기 타임아웃은 UNAVAILABLE이다`() {
        assertUnavailable(withException(SocketTimeoutException("read timed out")))
    }

    @Test
    fun `200인데 본문이 없으면 UNAVAILABLE이다`() {
        assertUnavailable(withSuccess("", MediaType.APPLICATION_JSON))
    }

    @Test
    fun `요청은 계약 버전과 공유 비밀을 싣고 셀러 식별자와 이미지는 싣지 않는다`() {
        server
            .expect(requestTo("$BASE_URL/internal/seller-draft"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header(AiDraftClientAdapter.SECRET_HEADER, SECRET))
            .andExpect(jsonPath("$.schemaVersion").value(1))
            .andExpect(jsonPath("$.requestId").value("req-1"))
            .andExpect(jsonPath("$.productName").value("모모의 하루 목줄 M"))
            .andExpect(jsonPath("$.categoryId").value(152))
            .andExpect(jsonPath("$.lockedFields.optionGroups[0].name").value("사이즈"))
            .andExpect(jsonPath("$.sellerId").doesNotExist())
            .andExpect(jsonPath("$.image").doesNotExist())
            .andRespond(withSuccess(SKIPPED_RESPONSE_JSON, MediaType.APPLICATION_JSON))

        adapter.generate(request())

        server.verify()
    }

    private fun assertUnavailable(response: org.springframework.test.web.client.ResponseCreator) {
        server.expect(requestTo("$BASE_URL/internal/seller-draft")).andRespond(response)

        val result = adapter.generate(request())

        assertEquals(ProductDraftAutofillResult.State.UNAVAILABLE, result.state, "외부 실패는 예외가 아니라 결과다")
        assertNull(result.draft.description, "실패면 폼을 빈 상태로 그대로 둔다")
        assertTrue(result.fieldStatus.isEmpty())
        server.verify()
    }

    private fun request() =
        ProductDraftAutofillRequest(
            requestId = "req-1",
            productName = "모모의 하루 목줄 M",
            categoryId = 152L,
            lockedFields =
                ProductDraftAutofillRequest.LockedFields(
                    description = null,
                    optionGroups = listOf(ProductDraftAutofillRequest.OptionGroup("사이즈", listOf("S", "M"))),
                ),
        )

    companion object {
        private const val BASE_URL = "http://ai.internal:8200"
        private const val SECRET = "test-secret"

        /**
         * 모델이 채운 응답. **우리가 안 읽는 운영 축(usage·guardDrops·itemSignal·image·
         * draftVersion·generatedAt)을 일부러 함께 싣는다** — 모르는 필드에 역직렬화가 터지면
         * AI가 응답에 한 줄을 더하는 날 셀러 화면이 깨진다.
         */
        private val FILLED_RESPONSE_JSON =
            """
            {
              "requestId": "req-1",
              "draftVersion": "draft-v1+catalog-v1",
              "state": "OK",
              "itemSignal": { "grade": "PINNED", "resolvedBy": "SELLER_LEAF_CATEGORY", "matchedTerms": ["목줄"] },
              "image": { "usability": "NO_IMAGE_SENT", "skippedByMode": true },
              "imageUsability": "NO_IMAGE_SENT",
              "imageMode": "OFF",
              "draft": {
                "categoryId": 152,
                "productName": null,
                "description": "모모의 하루 목줄입니다.",
                "regularPrice": null,
                "optionGroups": [ { "name": "사이즈", "values": ["S", "M"] } ],
                "optionCombinations": [
                  { "optionValues": ["S"], "additionalPrice": 0, "stockQuantity": null },
                  { "optionValues": ["M"], "additionalPrice": 0, "stockQuantity": null }
                ]
              },
              "basis": { "categoryId": "SELLER_CATEGORY", "description": "NAME" },
              "fieldStatus": { "categoryId": "FILLED", "description": "FILLED", "regularPrice": "EMPTY_NOT_ATTEMPTED" },
              "guardDrops": [ { "field": "description", "kind": "LENGTH", "detail": "너무 길다" } ],
              "sellerNotice": "상품명이 길어 설명을 짧게 잡았습니다.",
              "modelCalled": true,
              "skipReason": null,
              "reusedFromCache": false,
              "usage": [ { "requestId": "req-1", "callId": "c1", "stage": "DRAFT", "modelId": "claude-haiku-4-5", "costMicroUsd": 1200, "latencyMs": 4100 } ],
              "failureDetail": null,
              "generatedAt": "2026-10-30T00:06:45.224967Z",
              "schemaVersion": 1
            }
            """.trimIndent()

        /** 실제 응답 표본(API 키 없는 dev에서 왕복해 받은 것). state=OK인데 전 필드가 비어 있다. */
        private val SKIPPED_RESPONSE_JSON =
            """
            {
              "requestId": "req-1",
              "draftVersion": "draft-v1+catalog-v1",
              "state": "OK",
              "itemSignal": { "grade": "PINNED", "resolvedBy": "SELLER_LEAF_CATEGORY", "matchedTerms": ["목줄"], "candidateLeafIds": [152], "species": "DOG" },
              "image": { "usability": "NO_IMAGE_SENT", "mediaType": null, "widthPx": 0, "skippedByMode": true },
              "imageUsability": "NO_IMAGE_SENT",
              "imageMode": "OFF",
              "draft": {
                "categoryId": null, "productName": null, "description": null, "regularPrice": null,
                "discountPrice": null, "discountStartAt": null, "discountEndAt": null,
                "thumbnailImageKey": null, "additionalImageKeys": null, "detailImageKeys": null,
                "optionGroups": null, "optionCombinations": null
              },
              "basis": { "categoryId": "NONE", "description": "NONE", "optionGroups": "NONE" },
              "fieldStatus": {
                "productName": "EMPTY_NOT_ATTEMPTED", "categoryId": "EMPTY_CALL_FAILED",
                "description": "EMPTY_CALL_FAILED", "optionGroups": "EMPTY_CALL_FAILED",
                "optionCombinations": "EMPTY_CALL_FAILED"
              },
              "guardDrops": [],
              "sellerNotice": null,
              "modelCalled": false,
              "skipReason": "LLM_UNAVAILABLE",
              "reusedFromCache": false,
              "usage": [],
              "failureDetail": null,
              "generatedAt": "2026-10-30T00:06:45.224967Z",
              "schemaVersion": 1
            }
            """.trimIndent()

        private val FAILED_RESPONSE_JSON =
            """
            {
              "requestId": "req-1",
              "state": "FAILED",
              "draft": { "categoryId": null, "description": null, "optionGroups": null, "optionCombinations": null },
              "basis": {},
              "fieldStatus": { "description": "EMPTY_CALL_FAILED" },
              "sellerNotice": null,
              "modelCalled": true,
              "failureDetail": "모델 호출이 죽었다",
              "generatedAt": "2026-10-30T00:06:45.224967Z"
            }
            """.trimIndent()
    }
}
