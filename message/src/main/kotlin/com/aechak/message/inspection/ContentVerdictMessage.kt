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
    /**
     * 모델이 이 내용을 실제로 읽었다고 볼 수 있는 판정인가.
     *
     * 노출 게이트가 통과 판정으로 상품을 되살릴지 가르는 술어다. 계약 클래스에 둔 이유는
     * **계약 표본과 이 술어가 서로 묶여 있어야 하기 때문**이다. 둘이 떨어져 있던 동안 같은
     * 레포 안에서 표본은 "모델 관점 행 없이 통과"를 선별의 정상 경로로 적고 게이트는 그것을
     * 증거 없는 통과로 다뤘고, 양쪽 수트가 모두 녹색이라 CI 로는 영원히 안 잡혔다.
     *
     * 모델 관점 행 가운데 하나라도 결과를 낸 것이 있으면 참이다 — 위반을 낸 관점도 증거로
     * 센다. 여기서 보는 것은 무엇을 찾았는지가 아니라 읽었는지다.
     *
     * 선별이 통과로 가른 건은 예외로 받는다. 그것은 판단이고 장애가 아니다. 다만 건너뛴
     * 관점마다 사유가 적혀 있어야 하고(사유가 없으면 호출이 죽어 못 본 것과 구분되지 않는다)
     * 모델 관점 행이 아예 없으면 무엇을 봤는지 알 길이 없어 증거로 세지 않는다.
     */
    fun hasModelEvidence(): Boolean {
        val modelRows = perspectives.filter { it.perspective in MODEL_PERSPECTIVES }
        if (modelRows.any { it.outcome != PerspectiveOutcome.UNCHECKED }) return true

        val triageCleared =
            perspectives.any {
                it.perspective == InspectionPerspective.TRIAGE && it.outcome == PerspectiveOutcome.CLEAR
            }
        return triageCleared &&
            modelRows.isNotEmpty() &&
            modelRows.all { it.skipReason == TRIAGE_SKIP_REASON }
    }

    companion object {
        const val SCHEMA_VERSION = 1

        /** 모델이 내용을 읽고 결과를 내는 관점. RULE과 TRIAGE는 모델 판정이 아니라 제외한다 */
        val MODEL_PERSPECTIVES =
            setOf(
                InspectionPerspective.ALL,
                InspectionPerspective.DISPLAY_ADS,
                InspectionPerspective.CONDUCT_TRADE,
            )

        /** 선별이 모델 호출을 일부러 건너뛸 때 그 관점 행에 적히는 사유. AI 서버와 맞춰 둔 값이다 */
        const val TRIAGE_SKIP_REASON = "TRIAGE_CLEAR"
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
    /** SUCCESS, TIMEOUT, RATE_LIMITED, BUDGET_EXHAUSTED, REFUSED, SCHEMA_INVALID, TRUNCATED, ERROR */
    val outcome: String,
)
