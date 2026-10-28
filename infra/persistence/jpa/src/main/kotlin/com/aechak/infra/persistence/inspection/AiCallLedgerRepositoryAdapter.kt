package com.aechak.infra.persistence.inspection

import com.aechak.domain.inspection.AiCallLedgerEntry
import com.aechak.domain.inspection.repository.AiCallLedgerRepository
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository

/**
 * 호출 원장 적재.
 *
 * JPA가 아니라 JDBC로 쓰는 이유는 중복을 무시하는 삽입이 필요하기 때문이다.
 * 판정이 다시 전달되면 같은 `callId`가 또 오는데, 그때 예외가 올라오면 판정 적용까지
 * 함께 롤백된다. 원장은 회계 기록이지 판정의 전제가 아니므로 조용히 넘겨야 한다.
 *
 * `INSERT IGNORE`를 쓰지 않는다. 그 구문은 유일키 충돌만이 아니라 다른 오류까지 경고로
 * 낮추기 때문에, 컬럼 길이 초과 같은 실수가 조용히 잘린 값으로 들어간다.
 * 중복만 무시하려면 충돌 시 아무것도 바꾸지 않는 갱신절을 쓰는 쪽이 정확하다.
 *
 * 감사 컬럼이 NOT NULL이라 함께 세팅한다 — 이 경로는 JPA 콜백을 타지 않는다.
 */
@Repository
class AiCallLedgerRepositoryAdapter(
    private val db: JdbcClient,
) : AiCallLedgerRepository {
    override fun insertIgnoringDuplicates(entries: List<AiCallLedgerEntry>): Int {
        if (entries.isEmpty()) return 0

        return entries.sumOf { entry ->
            db
                .sql(
                    """
                    INSERT INTO ai_call_ledger
                        (request_id, call_id, inspection_id, purpose, perspective, model_id, prompt_version,
                         input_tokens, cache_read_input_tokens, cache_creation_input_tokens, output_tokens,
                         cost_micro_usd, latency_ms, outcome, created_at, updated_at)
                    VALUES
                        (:requestId, :callId, :inspectionId, :purpose, :perspective, :modelId, :promptVersion,
                         :inputTokens, :cacheRead, :cacheCreation, :outputTokens,
                         :costMicroUsd, :latencyMs, :outcome, NOW(6), NOW(6))
                    ON DUPLICATE KEY UPDATE call_id = call_id
                    """.trimIndent(),
                ).param("requestId", entry.requestId)
                .param("callId", entry.callId)
                .param("inspectionId", entry.inspectionId)
                .param("purpose", entry.purpose)
                .param("perspective", entry.perspective)
                .param("modelId", entry.modelId)
                .param("promptVersion", entry.promptVersion)
                .param("inputTokens", entry.inputTokens)
                .param("cacheRead", entry.cacheReadInputTokens)
                .param("cacheCreation", entry.cacheCreationInputTokens)
                .param("outputTokens", entry.outputTokens)
                .param("costMicroUsd", entry.costMicroUsd)
                .param("latencyMs", entry.latencyMs)
                .param("outcome", entry.outcome)
                .update()
        }
    }
}
