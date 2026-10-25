package com.aechak.infra.persistence.inspection

import com.aechak.application.inspection.port.InspectionMetricsQueryPort
import com.aechak.application.inspection.port.InspectionMetricsWindow
import com.aechak.application.inspection.port.view.InspectionBacklogCounts
import com.aechak.application.inspection.port.view.InspectionCostCounts
import com.aechak.application.inspection.port.view.InspectionCoverageCounts
import com.aechak.application.inspection.port.view.InspectionFunnelCounts
import com.aechak.application.inspection.port.view.InspectionStageCost
import com.aechak.application.inspection.port.view.InspectionTriageCounts
import com.aechak.domain.inspection.QAiCallLedgerEntry
import com.aechak.domain.inspection.QContentInspection
import com.aechak.domain.inspection.QInspectionFinding
import com.aechak.domain.inspection.enums.InspectionPerspectiveKind
import com.aechak.domain.inspection.enums.InspectionTargetType
import com.aechak.domain.inspection.enums.PerspectiveOutcome
import com.querydsl.core.types.Predicate
import com.querydsl.core.types.dsl.NumberPath
import com.querydsl.jpa.JPAExpressions
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Repository

private val inspection = QContentInspection.contentInspection
private val finding = QInspectionFinding.inspectionFinding
private val ledger = QAiCallLedgerEntry.aiCallLedgerEntry

/** 원장에 적히는 선별 관점 이름. 열거가 아니라 문자열 칼럼이라 값으로 비교한다 */
private val TRIAGE_PERSPECTIVE = InspectionPerspectiveKind.TRIAGE.name

/** 호출 성공을 뜻하는 원장 값. AI 레포의 `CallOutcome.SUCCESS`와 같은 문자열이다 */
private const val CALL_SUCCESS = "SUCCESS"

/**
 * 지표를 검수 행·관점 기록·호출 원장·검토 큐에서 세어 온다.
 *
 * 검수와 관점 기록·호출 원장은 연관이 아니라 값 참조(`inspection_id`)다. 조인 대신 상관
 * 서브쿼리(`exists`)로 묻는다 — 조인하면 한 검수에 관점 행이 여럿이라 분모가 행 수만큼
 * 부풀고, 그걸 `countDistinct`로 다시 접는 모양이 된다.
 *
 * **창 기준은 전부 `content_inspections.settled_at`이다.** 원장 시각과 검수 시각을 섞어 쓰면
 * 창 경계에 걸친 건이 지표마다 다른 버킷에 들어가고, 원장에는 `policy_version`이 없어 정책
 * 버전별 비교를 원장 앵커로는 할 수 없다. 그래서 원장은 `inspection_id`로 붙인다.
 *
 * 묶음 분류(퍼널·누락률)는 JPQL로 쓸 수 없어 [InspectionMetricsNativeQueries]가 맡는다.
 * 포트는 네이티브를 모른다.
 */
@Repository
class InspectionMetricsQueryAdapter(
    private val queryFactory: JPAQueryFactory,
    private val nativeQueries: InspectionMetricsNativeQueries,
) : InspectionMetricsQueryPort {
    override fun funnelCounts(window: InspectionMetricsWindow): InspectionFunnelCounts = nativeQueries.funnelCounts(window)

    override fun coverageCounts(window: InspectionMetricsWindow): InspectionCoverageCounts = nativeQueries.coverageCounts(window)

    override fun stageCosts(window: InspectionMetricsWindow): Map<String, InspectionStageCost> = nativeQueries.stageCosts(window)

    /**
     * 승급률의 분모·분자.
     *
     * 분모는 선별 **호출**이 나간 검수다. 관점 기록이 아니라 원장으로 센다 — 규칙이 적중해
     * 선별에 묻지도 않고 승급한 건에도 관점 행(`TRIAGE`/`UNCHECKED`/`RULE_HIT`)이 남기 때문에,
     * 기록으로 세면 호출하지도 않은 건이 분모에 들어와 승급률이 낮게 나온다.
     *
     * 건너뛴 건은 관점 행이 `CLEAR`인 것으로 가른다. 행의 유무로 가르면 승급 경로에도 행이
     * 남아서(`UNCHECKED`/`PROMOTED`) 승급 전건이 건너뜀으로 분류되고 1순위 지표가 뒤집힌다.
     */
    override fun triageCounts(window: InspectionMetricsWindow): InspectionTriageCounts {
        val triaged = countTriaged(window)
        if (triaged == 0L) return InspectionTriageCounts.empty()

        return InspectionTriageCounts(
            triaged,
            countTriaged(window, triageClearedExists(ledger.inspectionId)),
            // 호출이 깨져 강제 승급된 건. 승급으로 세지만 "선별이 판단해 올린 것"과 구분해 둔다.
            countTriaged(window, ledger.outcome.ne(CALL_SUCCESS)),
        )
    }

    /**
     * 창 안에 닫힌 검수에 붙은 호출의 지출.
     *
     * 건당 비용의 분모가 둘이다. `inspectionCount`는 **호출이 나간 검수**라 재시도와 등급
     * 승격이 분모를 부풀리지 않지만, 규칙이 무료로 차단한 건과 스텁 건이 빠진다. 유스케이스가
     * 창 안에 닫힌 검수 전수를 다른 분모로 함께 쓴다 — 1단 차단율만큼 건당 비용이 과대 보고되는
     * 것을 그 둘을 나란히 두는 것으로 막는다.
     */
    override fun costCounts(window: InspectionMetricsWindow): InspectionCostCounts {
        val callCount = countCalls(window)
        if (callCount == 0L) return InspectionCostCounts.empty()

        val inspectionCount =
            queryFactory
                .select(ledger.inspectionId.countDistinct())
                .from(ledger)
                .where(ledger.inspectionId.`in`(settledIds(window)))
                .fetchOne() ?: 0L

        return InspectionCostCounts(
            callCount,
            inspectionCount,
            sumCost(window),
            sumCost(window, ledger.perspective.eq(TRIAGE_PERSPECTIVE)),
            // 캐시가 조용히 죽은 호출. 에러 없이 입력 토큰이 전액 과금되는 자리다.
            countCalls(window, ledger.cacheReadInputTokens.eq(0)),
        )
    }

    /**
     * 지금 열려 있는 큐의 모양. 집계는 [InspectionMetricsNativeQueries]가 한 쿼리로 한다.
     *
     * **대상 종류를 못박는다.** 리뷰 검수는 노출 게이트가 조기 반환해 이 큐에 들어올 수 없으므로,
     * 상품이 아닌 축으로 좁힌 요청에는 빈 값을 돌려준다. 축을 무시하고 상품 수치를 실으면
     * 관리자는 "리뷰 검수 대기 N건, 임계 초과"를 읽는데 그 큐에는 리뷰가 한 건도 없다.
     *
     * 창과 정책 버전은 보지 않는다. 적체는 비율이 아니라 지금 열려 있는 큐의 모양이고, 큐 행에는
     * 정책 버전이 없다. 창을 걸면 창보다 오래 열려 있는 건이 빠지는데 그게 제일 먼저 봐야 하는
     * 건이다 — 정책 축으로 좁힌 요청에도 적체는 전체 큐라는 뜻이다.
     */
    override fun backlogCounts(window: InspectionMetricsWindow): InspectionBacklogCounts {
        val targetType = window.targetType()
        if (targetType != null && targetType != InspectionTargetType.PRODUCT) {
            return InspectionBacklogCounts.empty()
        }
        return nativeQueries.backlogCounts()
    }

    override fun promptVersionCounts(window: InspectionMetricsWindow): Map<String, Long> {
        val rows = inspection.count()
        return queryFactory
            .from(inspection)
            .where(*settledWindow(window), inspection.promptVersion.isNotNull)
            .select(inspection.promptVersion, rows)
            .groupBy(inspection.promptVersion)
            .fetch()
            .mapNotNull { row ->
                val version = row.get(inspection.promptVersion) ?: return@mapNotNull null
                version to (row.get(rows) ?: 0L)
            }.toMap()
    }

    private fun countTriaged(
        window: InspectionMetricsWindow,
        vararg extra: Predicate,
    ): Long =
        queryFactory
            .select(ledger.inspectionId.countDistinct())
            .from(ledger)
            .where(
                ledger.perspective.eq(TRIAGE_PERSPECTIVE),
                // 검수에 묶이지 않은 호출은 분모가 될 수 없다. 창도 검수 쪽에서 온다.
                ledger.inspectionId.`in`(settledIds(window)),
                *extra,
            ).fetchOne() ?: 0L

    private fun countCalls(
        window: InspectionMetricsWindow,
        vararg extra: Predicate,
    ): Long =
        queryFactory
            .select(ledger.count())
            .from(ledger)
            .where(ledger.inspectionId.`in`(settledIds(window)), *extra)
            .fetchOne() ?: 0L

    private fun sumCost(
        window: InspectionMetricsWindow,
        vararg extra: Predicate,
    ): Long =
        queryFactory
            .select(ledger.costMicroUsd.sumLong())
            .from(ledger)
            .where(ledger.inspectionId.`in`(settledIds(window)), *extra)
            .fetchOne() ?: 0L

    /** 창 안에 닫힌 검수의 id. 원장을 이 집합으로 붙여 앵커를 하나로 맞춘다 */
    private fun settledIds(window: InspectionMetricsWindow) =
        JPAExpressions
            .select(inspection.id)
            .from(inspection)
            .where(*settledWindow(window))

    /** 창(닫힌 시각) + 축. `from`은 포함, `to`는 배타다 */
    private fun settledWindow(window: InspectionMetricsWindow): Array<Predicate> {
        val predicates =
            mutableListOf<Predicate>(
                inspection.settledAt.goe(window.from()),
                inspection.settledAt.lt(window.to()),
            )
        window.policyVersion()?.let { predicates += inspection.policyVersion.eq(it) }
        window.targetType()?.let { predicates += inspection.targetType.eq(it) }
        return predicates.toTypedArray()
    }

    /** 선별이 "볼 필요 없다"고 판단한 기록이 그 검수에 있는가 */
    private fun triageClearedExists(inspectionId: NumberPath<Long>): Predicate =
        JPAExpressions
            .selectOne()
            .from(finding)
            .where(
                finding.inspectionId.eq(inspectionId),
                finding.perspective.eq(InspectionPerspectiveKind.TRIAGE),
                finding.outcome.eq(PerspectiveOutcome.CLEAR),
            ).exists()
}
