package com.aechak.infra.persistence.inspection

import com.aechak.application.inspection.port.InspectionMetricsWindow
import com.aechak.application.inspection.port.view.InspectionBacklogCounts
import com.aechak.application.inspection.port.view.InspectionCoverageCounts
import com.aechak.application.inspection.port.view.InspectionFunnelCounts
import com.aechak.application.inspection.port.view.InspectionStageCost
import jakarta.persistence.EntityManager
import jakarta.persistence.Query
import org.springframework.stereotype.Component
import java.sql.Timestamp
import java.time.LocalDateTime

/**
 * 네이티브로만 되는 집계 — 검수 한 건의 관점 기록을 한 줄로 접는 것.
 *
 * JPQL로 쓸 수 없는 것이 이것이다. 탈출 버킷은 관점 플래그 여러 개의 조합으로 정해지는데,
 * 상관 서브쿼리를 버킷마다 돌리면 같은 관점 테이블을 여덟 번 읽고 **그 여덟 조건이 서로
 * 배타라는 보장도 코드에 흩어진다**. 묶음 한 번으로 접으면 한 검수가 정확히 한 버킷에
 * 들어가는 것이 쿼리 안에서 보증된다. 누락률이 같은 접기를 쓰는 이유도 같다 — 증거 판정이
 * 관점 행 전체를 봐야 결정되는 술어라서, 노출 게이트의 그 판정을 거울로 베끼려면 접기가 먼저다.
 *
 * 분위수(p50·p95)도 JPQL에 윈도 함수가 없어 네이티브가 필요하지만 이 범위에 넣지 않았다.
 *
 * 분류를 `judgment` + 관점 플래그로만 한다. 계약 필드 `criticOverturned`를 읽지 않는다 —
 * 그 필드는 저장되지 않고, 통과 판정에 모델 위반 관점 행이 남는 길은 뒤집힘뿐이라 플래그만으로
 * 복원된다. 비평이 뒤집을 때 파이프라인이 위반 목록만 비우고 관점 행은 그대로 남기기 때문이다.
 *
 * MySQL 8.4 기준(운영·테스트 컨테이너 양쪽)이다. `MAX(불린식)`으로 묶음 플래그를 만드는 것은
 * MySQL의 불린을 0/1 정수로 다루는 성질에 기댄다.
 */
@Component
class InspectionMetricsNativeQueries(
    private val em: EntityManager,
) {
    /**
     * 탈출 분포와 판정 분포, 그리고 단계별 분모·분자를 한 쿼리로 센다.
     *
     * 관점 기록을 **왼쪽 조인**한다. 안쪽 조인이면 관점 행이 하나도 없는 검수가 분모에서 빠져
     * 탈출 분포의 합이 설정 건수에 못 미친다.
     *
     * 분기 순서가 배타성을 만들므로 `CASE`의 순서가 규약이다. 실패 터미널 둘을 먼저 걸러야
     * 미판정 건이 1단에 들어오지 않는다.
     */
    fun funnelCounts(window: InspectionMetricsWindow): InspectionFunnelCounts {
        val row =
            bind(
                em.createNativeQuery(
                    """
                    SELECT COUNT(*),
                           COALESCE(SUM(t.judgment = 'PASS'), 0),
                           COALESCE(SUM(t.judgment = 'FLAG'), 0),
                           COALESCE(SUM(t.judgment = 'UNJUDGED'), 0),
                           COALESCE(SUM(t.judgment = 'FAILED'), 0),
                           COALESCE(SUM(t.exit_code = 'RULE_BLOCKED'), 0),
                           COALESCE(SUM(t.exit_code = 'TRIAGE_CLEARED'), 0),
                           COALESCE(SUM(t.exit_code = 'MODEL_CLEAR'), 0),
                           COALESCE(SUM(t.exit_code = 'CRITIC_OVERTURNED'), 0),
                           COALESCE(SUM(t.exit_code = 'MODEL_FLAG'), 0),
                           COALESCE(SUM(t.exit_code = 'VERDICT_FAILED'), 0),
                           COALESCE(SUM(t.exit_code = 'UNJUDGED'), 0),
                           COALESCE(SUM(t.exit_code = 'UNCLASSIFIED'), 0),
                           COALESCE(SUM(t.model_viol), 0),
                           COALESCE(SUM(t.model_clear = 1 AND t.model_viol = 0), 0),
                           COALESCE(SUM(t.model_dispatched), 0),
                           COALESCE(SUM(t.rule_hit_bypass), 0),
                           COALESCE(SUM(t.expired_marker), 0),
                           COALESCE(SUM(t.no_reason), 0)
                    FROM (
                        SELECT g.*,
                               CASE
                                   WHEN g.judgment = 'FAILED' THEN 'VERDICT_FAILED'
                                   WHEN g.judgment = 'UNJUDGED' THEN 'UNJUDGED'
                                   WHEN g.judgment = 'FLAG' AND g.rule_viol = 1
                                        AND g.triage_row = 0 AND g.model_row = 0 THEN 'RULE_BLOCKED'
                                   WHEN g.judgment = 'PASS' AND g.triage_clear = 1 THEN 'TRIAGE_CLEARED'
                                   WHEN g.judgment = 'PASS' AND g.model_clear = 1
                                        AND g.model_viol = 0 THEN 'MODEL_CLEAR'
                                   WHEN g.judgment = 'PASS' AND g.model_viol = 1 THEN 'CRITIC_OVERTURNED'
                                   WHEN g.judgment = 'FLAG' AND g.model_viol = 1 THEN 'MODEL_FLAG'
                                   ELSE 'UNCLASSIFIED'
                               END AS exit_code
                        FROM (${funnelFlags(window)}) g
                    ) t
                    """.trimIndent(),
                ),
                window,
            ).singleResult as Array<*>

        return InspectionFunnelCounts(
            row.long(0),
            row.long(1),
            row.long(2),
            row.long(3),
            row.long(4),
            row.long(5),
            row.long(6),
            row.long(7),
            row.long(8),
            row.long(9),
            row.long(10),
            row.long(11),
            row.long(12),
            row.long(13),
            row.long(14),
            row.long(15),
            row.long(16),
            row.long(17),
            row.long(18),
        )
    }

    /**
     * 누락률의 분모·분자.
     *
     * 증거 술어가 **노출 게이트의 증거 판정을 그대로 베낀 것**이다. 그 판정은 판정 수신 경로가
     * 메시지에서 계산하고 어디에도 저장하지 않아서 지표가 다시 계산할 수밖에 없고, 두 계산이
     * 갈라지면 "지표는 누락이라는데 큐에는 안 올라온" 상태가 조용히 생긴다.
     *
     * 첫 분기는 모델 관점 중 하나라도 결과를 낸 것이 있으면 참이다 — 위반을 낸 관점도 증거로
     * 센다. 여기서 보는 것은 무엇을 찾았는지가 아니라 읽었는지다. 둘째 분기는 선별이 통과로
     * 가른 건이다. 그것은 판단이고 장애가 아니다. 호출이 죽어 건너뛴 것과 가르는 축은 사유
     * 하나뿐이라 사유가 규약이 된다. 모델 관점 행이 아예 없으면 무엇을 봤는지 알 길이 없으므로
     * 증거로 세지 않는다.
     */
    fun coverageCounts(window: InspectionMetricsWindow): InspectionCoverageCounts {
        val verified =
            """
            ((t.model_viol = 1 OR t.model_clear = 1)
             OR (t.triage_clear = 1 AND t.model_row = 1 AND t.model_skipped_other = 0))
            """.trimIndent()
        val row =
            bind(
                em.createNativeQuery(
                    """
                    SELECT COUNT(*),
                           COALESCE(SUM(t.judgment = 'UNJUDGED'), 0),
                           COALESCE(SUM(t.judgment = 'FAILED'), 0),
                           COALESCE(SUM(t.judgment = 'PASS' AND NOT $verified), 0),
                           COALESCE(SUM(t.judgment = 'PASS' AND t.triage_clear = 1), 0)
                    FROM (${funnelFlags(window)}) t
                    """.trimIndent(),
                ),
                window,
            ).singleResult as Array<*>

        return InspectionCoverageCounts(row.long(0), row.long(1), row.long(2), row.long(3), row.long(4))
    }

    /** 단계별 지출. 단계를 복원할 수 없는 행(`ALL`·관점 없음)은 `UNATTRIBUTED`로 접는다 */
    fun stageCosts(window: InspectionMetricsWindow): Map<String, InspectionStageCost> {
        val rows =
            bind(
                em.createNativeQuery(
                    """
                    SELECT l.perspective,
                           COUNT(*),
                           COALESCE(SUM(l.cost_micro_usd), 0),
                           COALESCE(SUM(l.cache_read_input_tokens = 0), 0)
                    FROM ai_call_ledger l
                             JOIN content_inspections i ON i.id = l.inspection_id
                    WHERE ${settledWhere(window)}
                    GROUP BY l.perspective
                    """.trimIndent(),
                ),
                window,
            ).resultList

        val merged = LinkedHashMap<String, InspectionStageCost>()
        rows.forEach { raw ->
            val row = raw as Array<*>
            val stage = stageOf(row[0] as String?)
            val current = merged[stage]
            merged[stage] =
                InspectionStageCost(
                    (current?.callCount() ?: 0) + row.long(1),
                    (current?.costMicroUsd() ?: 0) + row.long(2),
                    (current?.cacheMissCallCount() ?: 0) + row.long(3),
                )
        }
        return merged
    }

    /**
     * 지금 열려 있는 큐의 모양 — 건수, 가장 오래 기다린 유입 시각, 최신 판정 분포.
     *
     * 한 쿼리의 집계로 낸다. 열린 행을 적재해 메모리에서 세면 임계가 가리키려는 상태(수만 행이
     * 쌓여 아무도 비우지 않는 큐)에서 그 적재가 가장 커진다 — 경보를 만드는 조회가 경보
     * 상황에서 가장 무거워지고, 그것을 어드민이 새로고침마다 돌린다.
     *
     * **대기 시계는 `created_at`이다.** `blinded_at`은 "행을 열었거나 다시 연 판정의 시각"이라
     * 열린 행에 새 판정이 올 때마다 덮인다. 그것으로 재면 관리자가 한 번도 보지 않은 행이 매일
     * 새 판정에 되감겨 영원히 "방금부터 기다림"으로 보고되고 시간 임계에 닿지 않는다.
     * `blinded_at`은 어드민 목록의 정렬 키로만 남긴다.
     *
     * 필터는 관리자 큐 목록과 같다 — 상품·셀러를 안쪽 조인한다. 그 조인 없이 세면 지표가
     * "대기 50건"이라 말하는데 화면에는 3건만 보인다. 사람이 처리할 수 있는 건수가 적체의 정의다.
     */
    fun backlogCounts(): InspectionBacklogCounts {
        val rows =
            em
                .createNativeQuery(
                    """
                    SELECT COALESCE(j.judgment, '${InspectionBacklogCounts.NO_VERDICT}') AS bucket,
                           COUNT(*),
                           MIN(q.created_at)
                    FROM product_review_queue_items q
                             JOIN products p ON p.id = q.target_id
                             JOIN sellers s ON s.user_id = p.seller_id
                             LEFT JOIN content_inspections j
                                       ON j.id = (SELECT MAX(i.id)
                                                  FROM content_inspections i
                                                  WHERE i.target_type = 'PRODUCT'
                                                    AND i.target_ref = q.target_id)
                    WHERE q.target_type = 'PRODUCT'
                      AND q.status = '$PENDING_REVIEW'
                    GROUP BY bucket
                    """.trimIndent(),
                ).resultList
                .map { it as Array<*> }

        val open = rows.sumOf { it.long(1) }
        if (open == 0L) return InspectionBacklogCounts.empty()

        return InspectionBacklogCounts(
            open,
            rows.mapNotNull { it.time(2) }.minOrNull(),
            rows.associate { (it[0] as String) to it.long(1) },
        )
    }

    /**
     * 검수 한 건을 관점 플래그 한 줄로 접는 서브쿼리.
     *
     * `MAX`가 행이 없을 때 NULL을 주므로 전부 `COALESCE`로 0으로 내린다 — NULL이 섞이면
     * 바깥의 `AND` 비교가 NULL이 되어 그 건이 어느 버킷에도 들어가지 않는다.
     *
     * 모델 관점 목록에 `ALL`을 넣는다. 관점 팬아웃을 끈 단일 판정의 실제 관점 값이기 때문이다.
     * 대신 기한 만료 표식은 `model_row`에서 **표식 자체로** 뺀다 — 그 행도 `ALL`로 적히는데
     * 모델이 본 것이 아니다. 판정값으로 간접 거르지 않는 이유가 있다. "만료 표식이 있는 건은
     * 반드시 미판정"이라는 전제는 지금 참이지만(늦게 온 판정은 버려진다) 그 전제가 깨지는 날
     * 지표가 에러 없이 틀어진다. 표식을 직접 보면 전제가 필요 없다.
     *
     * 기한 만료 표식의 제외 규약은 세 플래그가 같다 — `model_row`·`model_skipped_other`·
     * `model_dispatched` 모두 그 행을 모델이 본 것으로 세지 않는다. 하나만 빠뜨리면 표식이 적힌
     * 뒤 같은 행에 판정이 적히는 날(그 어긋남을 감시하려고 만든 경보가 가리키는 바로 그 상황)
     * 누락률이 노출 게이트의 판단과 갈라진다 — 지표는 누락이라는데 큐에는 안 올라온 상태다.
     *
     * `model_dispatched`는 호출이 실제로 나간 것만 센다. 선별이 건너뛴 것(TRIAGE_CLEAR)·
     * 스텁 모드(LLM_DISABLED)·기한 만료 표식을 제외한다. 폐기 설계는 이 자리를 "모델 행이 있고
     * 선별이 통과시키지 않았고 미판정이 아닌 건"으로 잡았는데 스텁 건이 분모에 들어온다 —
     * 그 건의 판정은 실패이지 미판정이 아니라서 걸러지지 않는다. 그러면 3단 검출율이 모델
     * 가용성이 아니라 조립 상태를 재게 된다.
     */
    private fun funnelFlags(window: InspectionMetricsWindow): String {
        val model = "f.perspective IN ('ALL', 'DISPLAY_ADS', 'CONDUCT_TRADE')"
        val notDispatched = "'$TRIAGE_CLEAR', '$LLM_DISABLED', '$WAIT_LIMIT_EXCEEDED'"
        return """
            SELECT i.id       AS id,
                   i.judgment AS judgment,
                   COALESCE(MAX(f.perspective = 'RULE' AND f.outcome = 'VIOLATION'), 0)       AS rule_viol,
                   COALESCE(MAX(f.perspective = 'TRIAGE'), 0)                                 AS triage_row,
                   COALESCE(MAX(f.perspective = 'TRIAGE' AND f.outcome = 'CLEAR'), 0)         AS triage_clear,
                   COALESCE(MAX(f.perspective = 'TRIAGE' AND f.outcome = 'UNCHECKED'
                                AND f.skip_reason = '$RULE_HIT'), 0)                          AS rule_hit_bypass,
                   COALESCE(MAX($model AND NOT (f.outcome = 'UNCHECKED'
                                AND COALESCE(f.skip_reason, '') = '$WAIT_LIMIT_EXCEEDED')), 0) AS model_row,
                   COALESCE(MAX($model AND f.outcome = 'VIOLATION'), 0)                       AS model_viol,
                   COALESCE(MAX($model AND f.outcome = 'CLEAR'), 0)                           AS model_clear,
                   COALESCE(MAX($model AND f.outcome = 'UNCHECKED'
                                AND COALESCE(f.skip_reason, '') NOT IN
                                    ('$TRIAGE_CLEAR', '$WAIT_LIMIT_EXCEEDED')), 0)           AS model_skipped_other,
                   COALESCE(MAX($model AND (f.outcome <> 'UNCHECKED'
                                OR COALESCE(f.skip_reason, '') NOT IN ($notDispatched))), 0)  AS model_dispatched,
                   COALESCE(MAX(f.perspective = 'ALL' AND f.outcome = 'UNCHECKED'
                                AND f.skip_reason = '$WAIT_LIMIT_EXCEEDED'), 0)               AS expired_marker,
                   COALESCE(MAX(f.outcome = 'UNCHECKED' AND f.skip_reason IS NULL), 0)        AS no_reason
            FROM content_inspections i
                     LEFT JOIN inspection_findings f ON f.inspection_id = i.id
            WHERE ${settledWhere(window)}
            GROUP BY i.id, i.judgment
            """.trimIndent()
    }

    private fun bind(
        query: Query,
        window: InspectionMetricsWindow,
    ): Query {
        query.setParameter("windowFrom", window.from())
        query.setParameter("windowTo", window.to())
        window.policyVersion()?.let { query.setParameter("policyVersion", it) }
        window.targetType()?.let { query.setParameter("targetType", it.name) }
        return query
    }

    /** 검수가 닫힌 시각 기준 창 + 축. 별칭은 `i`로 고정한다 */
    private fun settledWhere(window: InspectionMetricsWindow): String =
        "i.settled_at >= :windowFrom AND i.settled_at < :windowTo${axisWhere(window)}"

    private fun axisWhere(window: InspectionMetricsWindow): String {
        val clauses = StringBuilder()
        if (window.policyVersion() != null) clauses.append(" AND i.policy_version = :policyVersion")
        if (window.targetType() != null) clauses.append(" AND i.target_type = :targetType")
        return clauses.toString()
    }

    private fun stageOf(perspective: String?): String =
        when (perspective) {
            // 지금은 단계를 복원할 수 없는 행이다. AI 쪽 관점 열거에 비평·수정 문구가 없어서
            // 팬아웃을 끈 단일 판정·비평·수정 문구 셋이 같은 `ALL` 값으로 적히고, purpose
            // 칼럼으로도 갈리지 않는다(대상 종류에서만 만들어져 상품·리뷰 둘뿐). 숨기지 않고
            // 따로 보여야 단계별 합이 총액에 못 미치는 이유를 알 수 있다.
            null, OVERLOADED_PERSPECTIVE -> UNATTRIBUTED

            else -> perspective
        }

    companion object {
        /** 단계를 복원할 수 없는 호출의 버킷 이름 */
        const val UNATTRIBUTED = "UNATTRIBUTED"

        /** 단일 판정·비평·수정 문구가 함께 쓰는 관점 값 */
        private const val OVERLOADED_PERSPECTIVE = "ALL"

        // 관점 기록의 사유 어휘. AI 레포·기한 정리와의 약속 문자열이라 값으로 비교한다.
        private const val TRIAGE_CLEAR = "TRIAGE_CLEAR"
        private const val LLM_DISABLED = "LLM_DISABLED"
        private const val RULE_HIT = "RULE_HIT"

        /** 기한 정리가 미판정으로 닫을 때 남기는 표식. 백엔드가 적는 유일한 사유다 */
        private const val WAIT_LIMIT_EXCEEDED = "WAIT_LIMIT_EXCEEDED"

        /** 열린 검토 행의 상태값. 열거가 아니라 문자열 칼럼이라 값으로 비교한다 */
        private const val PENDING_REVIEW = "PENDING_REVIEW"
    }
}

private fun Array<*>.long(index: Int): Long = (this[index] as Number?)?.toLong() ?: 0L

/** 드라이버가 Timestamp로 주는 자리도 있고 LocalDateTime으로 주는 자리도 있어 둘을 함께 받는다 */
private fun Array<*>.time(index: Int): LocalDateTime? =
    when (val value = this[index]) {
        null -> null
        is Timestamp -> value.toLocalDateTime()
        is LocalDateTime -> value
        else -> error("시각 칼럼의 타입을 모른다: ${value::class}")
    }
