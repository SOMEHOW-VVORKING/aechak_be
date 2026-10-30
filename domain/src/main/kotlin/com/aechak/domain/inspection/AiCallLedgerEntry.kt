package com.aechak.domain.inspection

import com.aechak.domain.support.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

/**
 * 모델 호출 한 번의 사용량 기록.
 *
 * 검수 한 건이 호출 여러 번을 낳는다. 관점이 둘이고, 실패하면 재시도하고, 위반이면 반박과
 * 수정 문구가 더 붙는다. 그래서 논리 요청(`requestId`)과 실제 호출(`callId`)을 따로 적는다.
 * 하나로 묶으면 재시도와 등급 승격 비용이 한 행에 묻혀 실제 지출을 되살릴 수 없다.
 *
 * 입력 토큰을 셋으로 나눠 적는 이유도 같다. 전체 입력이 세 값의 합이라서, 나누지 않으면
 * 캐시가 실제로 적중했는지와 그 덕에 얼마를 아꼈는지를 알 수 없다.
 */
@Entity
@Table(
    name = "ai_call_ledger",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_ai_call_ledger_call", columnNames = ["call_id"]),
    ],
    // 지표가 원장을 검수 id로 붙이기 때문에 필요하다 — 비율 전부의 앵커가 검수가 닫힌 시각이라
    // 원장 자체의 시각으로는 진입하지 않는다. 관점을 둘째 칼럼으로 둔 것은 승급률의 분모
    // (perspective='TRIAGE')와 단계별 비용이 그 축으로 묶기 때문이다.
    indexes = [Index(name = "ix_ai_call_ledger_inspection", columnList = "inspection_id, perspective")],
)
class AiCallLedgerEntry protected constructor(
    @Column(nullable = false, length = 100, updatable = false)
    val requestId: String,
    @Column(nullable = false, length = 100, updatable = false)
    val callId: String,
    @Column(updatable = false)
    val inspectionId: Long?,
    @Column(nullable = false, length = 40, updatable = false)
    val purpose: String,
    @Column(length = 30, updatable = false)
    val perspective: String?,
    @Column(nullable = false, length = 100, updatable = false)
    val modelId: String,
    @Column(nullable = false, length = 40, updatable = false)
    val promptVersion: String,
    @Column(nullable = false, updatable = false)
    val inputTokens: Int,
    @Column(nullable = false, updatable = false)
    val cacheReadInputTokens: Int,
    @Column(nullable = false, updatable = false)
    val cacheCreationInputTokens: Int,
    @Column(nullable = false, updatable = false)
    val outputTokens: Int,
    /** 백만분의 1 달러 단위. 센트로 적으면 건당 비용이 0으로 눌린다 */
    @Column(nullable = false, updatable = false)
    val costMicroUsd: Long,
    @Column(nullable = false, updatable = false)
    val latencyMs: Int,
    @Column(nullable = false, length = 30, updatable = false)
    val outcome: String,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0
        protected set

    companion object {
        @Suppress("LongParameterList")
        fun of(
            requestId: String,
            callId: String,
            inspectionId: Long?,
            purpose: String,
            perspective: String?,
            modelId: String,
            promptVersion: String,
            inputTokens: Int,
            cacheReadInputTokens: Int,
            cacheCreationInputTokens: Int,
            outputTokens: Int,
            costMicroUsd: Long,
            latencyMs: Int,
            outcome: String,
        ): AiCallLedgerEntry =
            AiCallLedgerEntry(
                requestId = requestId,
                callId = callId,
                inspectionId = inspectionId,
                purpose = purpose,
                perspective = perspective,
                modelId = modelId,
                promptVersion = promptVersion,
                inputTokens = inputTokens,
                cacheReadInputTokens = cacheReadInputTokens,
                cacheCreationInputTokens = cacheCreationInputTokens,
                outputTokens = outputTokens,
                costMicroUsd = costMicroUsd,
                latencyMs = latencyMs,
                outcome = outcome,
            )
    }
}
