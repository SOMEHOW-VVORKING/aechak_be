package com.aechak.infra.client.ai

import com.aechak.application.product.draft.port.ProductDraftAutofillPort
import com.aechak.application.product.draft.port.ProductDraftAutofillRequest
import com.aechak.application.product.draft.port.ProductDraftAutofillResult
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Conditional
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.client.ClientHttpResponse
import org.springframework.http.converter.HttpMessageConversionException
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import java.io.UncheckedIOException

/**
 * 셀러 폼 초안 포트의 AI 서버 어댑터. 동기 HTTP 한 번이다.
 *
 * **BusinessException을 던지지 않는다.** 이 어댑터의 전 실패 경로는 결과값(UNAVAILABLE)이다 —
 * 초안은 보조 기능이고, 셀러 잘못이 아닌 실패로 등록 화면에 5xx를 띄우면 등록이 깨진 것처럼 보인다.
 *
 * 비밀 헤더는 설정이 아니라 여기서 싣는다. 요청마다 붙는 그 한 줄이 이 기능의 유일한 인증이고
 * (AI는 비밀이 비면 전건 403이다), 어댑터가 싣는 쪽이어야 목 서버로 동봉을 단언할 수 있다.
 */
@Component
@Conditional(AiDraftClientEnabled::class)
class AiDraftClientAdapter(
    @Qualifier(AiDraftConfig.AI_DRAFT_REST_CLIENT) private val restClient: RestClient,
    private val properties: AiDraftProperties,
) : ProductDraftAutofillPort {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun generate(request: ProductDraftAutofillRequest): ProductDraftAutofillResult {
        val body =
            try {
                restClient
                    .post()
                    .uri(DRAFT_PATH)
                    .header(SECRET_HEADER, properties.sharedSecret)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(toBody(request))
                    .retrieve()
                    .onStatus({ it.isError }) { _, response ->
                        throw logAndSignal(request.requestId, response)
                    }.body(AiDraftResponseBody::class.java)
            } catch (e: AiDraftUnavailable) {
                return ProductDraftAutofillResult.unavailable()
            } catch (e: HttpMessageConversionException) {
                return unavailable(request.requestId, e)
            } catch (e: RestClientException) {
                return unavailable(request.requestId, e)
            } catch (e: UncheckedIOException) {
                return unavailable(request.requestId, e)
            }
        if (body == null) {
            log.warn("AI 초안 200 응답에 본문이 없다. requestId={}", request.requestId)
            return ProductDraftAutofillResult.unavailable()
        }
        log.info(
            "AI 초안 응답. requestId={}, state={}, filled={}, skipReason={}, modelCalled={}, cached={}, costMicroUsd={}, latencyMs={}",
            request.requestId,
            body.state,
            body.fieldStatus.count { it.value == FILLED },
            body.skipReason,
            body.modelCalled,
            body.reusedFromCache,
            body.usage.sumOf { it.costMicroUsd },
            body.usage.sumOf { it.latencyMs },
        )
        return toResult(body)
    }

    private fun toBody(request: ProductDraftAutofillRequest): AiDraftRequestBody =
        AiDraftRequestBody(
            requestId = request.requestId,
            productName = request.productName,
            categoryId = request.categoryId,
            lockedFields =
                request.lockedFields?.let { locked ->
                    AiLockedFields(
                        description = locked.description,
                        optionGroups = locked.optionGroups?.map { AiOptionGroup(it.name, it.values) },
                    )
                },
        )

    private fun toResult(body: AiDraftResponseBody): ProductDraftAutofillResult =
        ProductDraftAutofillResult(
            state = toState(body.state),
            draft =
                ProductDraftAutofillResult.Draft(
                    categoryId = body.draft.categoryId,
                    description = body.draft.description,
                    optionGroups =
                        body.draft.optionGroups.orEmpty().map {
                            ProductDraftAutofillResult.OptionGroup(it.name, it.values)
                        },
                    optionCombinations =
                        body.draft.optionCombinations.orEmpty().map {
                            ProductDraftAutofillResult.OptionCombination(it.optionValues, it.additionalPrice, it.stockQuantity)
                        },
                ),
            fieldStatus = body.fieldStatus,
            basis = body.basis,
            sellerNotice = body.sellerNotice,
        )

    /**
     * 열거로 직접 바꾸지 않는다 — AI가 상태값을 하나 더하는 날 valueOf가 터져 셀러가 500을 본다.
     * 모르는 값은 FAILED로 접는다(비어 있는데 사유를 모르는 쪽이 FAILED의 뜻이다).
     */
    private fun toState(state: String): ProductDraftAutofillResult.State =
        when (state) {
            "OK" -> {
                ProductDraftAutofillResult.State.OK
            }

            "FAILED" -> {
                ProductDraftAutofillResult.State.FAILED
            }

            else -> {
                log.warn("AI 초안이 모르는 state를 돌려줬다. state={}", state)
                ProductDraftAutofillResult.State.FAILED
            }
        }

    /**
     * onStatus 핸들러는 값을 못 돌려주므로 전용 예외로 올려 generate가 UNAVAILABLE로 바꾼다.
     *
     * 403·409는 사람이 고쳐야 하는 설정 어긋남이라 ERROR다 — AI는 403 사유를 자세히 적지
     * 않으므로(의도) 이 로그가 유일한 단서다. 503·422는 운영 중 일어날 수 있어 WARN이다.
     */
    private fun logAndSignal(
        requestId: String,
        response: ClientHttpResponse,
    ): AiDraftUnavailable {
        val status = response.statusCode.value()
        val body = runCatching { response.body.readNBytes(LOG_BODY_MAX_LENGTH).decodeToString() }.getOrDefault("")
        when (status) {
            HttpStatus.FORBIDDEN.value() -> {
                log.error("AI 초안 공유 비밀이 어긋났다(양쪽 SSM 값을 맞춰야 한다). requestId={}", requestId)
            }

            HttpStatus.CONFLICT.value() -> {
                log.error("AI 초안 계약 버전이 어긋났다(배포 순서). requestId={}, body={}", requestId, body)
            }

            else -> {
                log.warn("AI 초안 호출 실패. requestId={}, status={}, body={}", requestId, status, body)
            }
        }
        return AiDraftUnavailable()
    }

    private fun unavailable(
        requestId: String,
        e: Exception,
    ): ProductDraftAutofillResult {
        log.warn("AI 초안 호출이 끝나지 않았다(타임아웃·연결 실패·응답 해석 실패). requestId={}", requestId, e)
        return ProductDraftAutofillResult.unavailable()
    }

    companion object {
        const val SECRET_HEADER = "x-aechak-draft-secret"

        private const val DRAFT_PATH = "/internal/seller-draft"
        private const val FILLED = "FILLED"

        /** 오류 본문 읽기 상한. 프록시가 낸 HTML 전문까지 읽지 않게 끊는다 */
        private const val LOG_BODY_MAX_LENGTH = 500
    }
}

/** 외부 실패를 결과로 바꾸기 위한 내부 신호. 이 모듈 밖으로 나가지 않는다. */
private class AiDraftUnavailable : RuntimeException()
