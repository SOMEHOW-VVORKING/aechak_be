package com.aechak.message.inspection

import java.time.Instant

/**
 * AI 서버가 돌려주는 판정 결과. BE가 발행하지 않고 받기만 하므로 IntegrationMessage가 아니다.
 *
 * 파이썬 쪽이 같은 모양을 미러하고, 직렬화 표본을 양쪽이 공유해 왕복 테스트로 고정한다.
 * 엔벨로프의 payload는 객체가 아니라 JSON 문자열이라는 점이 미러링에서 가장 자주 틀리는 자리다.
 */
data class ContentVerdictMessage(
    val inspectionId: Long,
    val targetType: InspectionTargetType,
    val targetRef: Long,
    /** 요청에 실려 간 해시. 다르면 그 사이 내용이 바뀐 것이므로 적용하지 않는다 */
    val contentHash: String,
    val verdict: InspectionVerdict,
    /** 관점별 결과. 단일 판정일 때는 ALL 한 건만 온다 */
    val perspectives: List<PerspectiveResult>,
    val violations: List<Violation>,
    /** 셀러에게 보여 줄 수정 제안. 상품만 채워지고 리뷰는 null이다 */
    val sellerNotice: String?,
    /** 실제 모델 호출 단위 기록. 재시도와 단계 승격마다 한 건씩 늘어난다 */
    val calls: List<CallUsage>,
    /** 비평이 모델 위반을 뒤집어 통과로 바꿨는지. 어드민 검수 라우팅이 보는 신호다 */
    val criticOverturned: Boolean = false,
    val promptVersion: String,
    val schemaVersion: Int,
    val decidedAt: Instant,
) {
    companion object {
        const val SCHEMA_VERSION = 1
    }
}

/** 한 관점의 판정 결과. 통과와 미검사를 반드시 구분한다 */
data class PerspectiveResult(
    val perspective: InspectionPerspective,
    val outcome: PerspectiveOutcome,
    /** 미검사일 때 그 이유. 모델 오류·예산 소진·타임아웃 */
    val skipReason: String? = null,
)

/**
 * 위반 한 건.
 *
 * spanStart·spanEnd는 요청에 실어 보낸 본문 기준 오프셋이다. 리뷰는 요청 본문이 이미
 * 노출용으로 가려진 것이므로, 그 오프셋을 그대로 저장해도 마스킹이 풀리지 않는다.
 */
data class Violation(
    val category: ViolationCategory,
    val perspective: InspectionPerspective,
    val spanStart: Int?,
    val spanEnd: Int?,
    val quote: String?,
    val suggestion: String?,
    /** 0.0~1.0. 임계값은 설정으로 빼고 모델 출력에 섞지 않는다 */
    val confidence: Double?,
)

/**
 * 모델 호출 한 건의 사용량.
 *
 * 입력 토큰을 셋으로 나눠 적는다. 전체 입력이 세 값의 합이라서 나누지 않으면 비용을 되살릴 수 없다.
 * callId는 호출마다 다르고 requestId는 논리 요청마다 같다. 둘을 뭉개면 재시도 비용이 숨는다.
 */
data class CallUsage(
    val requestId: String,
    val callId: String,
    val perspective: InspectionPerspective,
    val modelId: String,
    val inputTokens: Int,
    val cacheReadInputTokens: Int,
    val cacheCreationInputTokens: Int,
    val outputTokens: Int,
    val costMicroUsd: Long,
    val latencyMs: Int,
    /** SUCCESS, TIMEOUT, RATE_LIMITED, BUDGET_EXHAUSTED, REFUSED, SCHEMA_INVALID, ERROR */
    val outcome: String,
)
