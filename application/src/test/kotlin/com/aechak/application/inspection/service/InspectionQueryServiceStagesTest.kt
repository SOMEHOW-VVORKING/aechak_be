package com.aechak.application.inspection.service

import com.aechak.application.inspection.usecase.result.InspectionStage
import com.aechak.application.inspection.usecase.result.InspectionStageResult
import com.aechak.domain.inspection.InspectionFinding
import com.aechak.domain.inspection.enums.InspectionPerspectiveKind
import com.aechak.domain.inspection.enums.InspectionProcessingState
import com.aechak.domain.inspection.enums.PerspectiveOutcome
import com.aechak.domain.inspection.repository.InspectionFindingRepository
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * [단위] 관점 행을 셀러가 보는 세 단계로 접는 규칙을 고정한다.
 *
 * 깨지면 셋 중 하나가 난다. 단계 수가 조회마다 달라져 화면이 줄을 세야 하거나, 모델이 보지
 * 못한 관점이 "문제 없음"으로 보여 셀러가 통과한 줄 알고 등록하거나, 끝내 돌지 않은 단계가
 * "아직"으로 보여 셀러가 오지 않을 결과를 기다린다.
 */
class InspectionQueryServiceStagesTest {
    private val findings = FakeInspectionFindingRepository()
    private val service =
        InspectionQueryService(
            inspections = unused(),
            reviewQueue = unused(),
            findings = findings,
            violationDeserializer = unused(),
        )

    @Test
    fun `관점 행이 없어도 세 단계를 순서대로 돌려준다`() {
        val stages = stages()

        assertEquals(
            listOf(InspectionStage.RULE, InspectionStage.TRIAGE, InspectionStage.MODEL),
            stages.map { it.stage },
            "길이나 순서가 갈리면 화면이 단계를 세야 한다",
        )
        assertEquals(List(3) { PerspectiveOutcome.UNCHECKED }, stages.map { it.outcome })
    }

    @Test
    fun `단계 키와 관점 결과의 값 집합을 못박는다`() {
        assertEquals(
            listOf("RULE", "TRIAGE", "MODEL"),
            InspectionStage.entries.map { it.name },
            "단계를 더하면 화면의 세 줄 전제가 깨진다. 계약을 먼저 고쳐라",
        )
        assertEquals(
            listOf("VIOLATION", "CLEAR", "UNCHECKED"),
            PerspectiveOutcome.entries.map { it.name },
            "셀러 응답이 이 열거를 그대로 내보낸다. 값을 더하면 FE 분기가 빠져 아무것도 안 그린다",
        )
    }

    @Test
    fun `관점 행이 일부만 있어도 세 단계다`() {
        given(InspectionPerspectiveKind.RULE, PerspectiveOutcome.CLEAR)

        val stages = stages()

        assertEquals(3, stages.size)
        assertEquals(PerspectiveOutcome.CLEAR, stage(stages, InspectionStage.RULE).outcome)
        assertEquals(PerspectiveOutcome.UNCHECKED, stage(stages, InspectionStage.TRIAGE).outcome)
        assertEquals(PerspectiveOutcome.UNCHECKED, stage(stages, InspectionStage.MODEL).outcome)
    }

    @Test
    fun `모델 관점 셋을 한 줄로 접는다`() {
        given(InspectionPerspectiveKind.ALL, PerspectiveOutcome.CLEAR)
        given(InspectionPerspectiveKind.DISPLAY_ADS, PerspectiveOutcome.CLEAR)
        given(InspectionPerspectiveKind.CONDUCT_TRADE, PerspectiveOutcome.CLEAR)

        assertEquals(3, stages().size, "관점이 늘어도 단계 수는 셋이다")
    }

    @Test
    fun `모델 접기는 하나라도 위반이면 위반이다`() {
        given(InspectionPerspectiveKind.DISPLAY_ADS, PerspectiveOutcome.CLEAR)
        given(InspectionPerspectiveKind.CONDUCT_TRADE, PerspectiveOutcome.VIOLATION)

        val model = stage(stages(), InspectionStage.MODEL)

        assertEquals(PerspectiveOutcome.VIOLATION, model.outcome, "통과가 위반을 가리면 반려 사유가 설명되지 않는다")
        assertNull(model.detail, "위반은 설명할 것이 없다")
    }

    @Test
    fun `모델 접기는 위반이 없고 통과가 있으면 통과다`() {
        given(InspectionPerspectiveKind.DISPLAY_ADS, PerspectiveOutcome.CLEAR)
        given(InspectionPerspectiveKind.CONDUCT_TRADE, PerspectiveOutcome.UNCHECKED, skipReason = "TIMEOUT")

        val model = stage(stages(), InspectionStage.MODEL)

        assertEquals(PerspectiveOutcome.CLEAR, model.outcome)
        assertNull(model.detail)
    }

    @Test
    fun `모델 접기는 전부 미검사면 미검사다`() {
        given(InspectionPerspectiveKind.ALL, PerspectiveOutcome.UNCHECKED, skipReason = "BUDGET_EXHAUSTED")
        given(InspectionPerspectiveKind.DISPLAY_ADS, PerspectiveOutcome.UNCHECKED, skipReason = "TIMEOUT")

        val model = stage(stages(), InspectionStage.MODEL)

        assertEquals(PerspectiveOutcome.UNCHECKED, model.outcome, "미검사를 통과로 올리면 모델이 못 본 것이 문제 없음이 된다")
    }

    @Test
    fun `모델 사유는 저장 순서가 아니라 관점 순서로 고른다`() {
        // 저장 순서를 관점 순서의 역으로 둔다 -- 리포지토리가 돌려준 순서를 쓰면 이 단정이 깨진다
        given(InspectionPerspectiveKind.CONDUCT_TRADE, PerspectiveOutcome.UNCHECKED, skipReason = "TIMEOUT")
        given(InspectionPerspectiveKind.DISPLAY_ADS, PerspectiveOutcome.UNCHECKED, skipReason = "ERROR")
        given(InspectionPerspectiveKind.ALL, PerspectiveOutcome.UNCHECKED, skipReason = "BUDGET_EXHAUSTED")

        assertEquals(
            "예산 한도에 걸려 호출하지 못했어요",
            stage(stages(), InspectionStage.MODEL).detail,
            "관점 순서가 고정이라 조회마다 같은 사유여야 한다",
        )
    }

    @Test
    fun `앞 관점에 행이 없으면 다음 관점의 사유를 쓴다`() {
        given(InspectionPerspectiveKind.CONDUCT_TRADE, PerspectiveOutcome.UNCHECKED, skipReason = "TIMEOUT")
        given(InspectionPerspectiveKind.DISPLAY_ADS, PerspectiveOutcome.UNCHECKED, skipReason = "ERROR")

        assertEquals(
            "모델 호출이 실패했어요",
            stage(stages(), InspectionStage.MODEL).detail,
            "ALL 이 비면 DISPLAY_ADS 다. 저장 순서를 따라가면 CONDUCT_TRADE 의 사유가 나온다",
        )
    }

    @Test
    fun `미검사 사유를 셀러가 읽는 문장으로 옮긴다`() {
        given(InspectionPerspectiveKind.TRIAGE, PerspectiveOutcome.UNCHECKED, skipReason = "RULE_HIT")

        assertEquals(
            "앞 단계에서 이미 걸려 더 보지 않았어요",
            stage(stages(), InspectionStage.TRIAGE).detail,
        )
    }

    @Test
    fun `요청이 몰린 것과 모델이 거부한 것을 따로 말한다`() {
        given(InspectionPerspectiveKind.ALL, PerspectiveOutcome.UNCHECKED, skipReason = "RATE_LIMITED")
        given(InspectionPerspectiveKind.TRIAGE, PerspectiveOutcome.UNCHECKED, skipReason = "REFUSED")

        val stages = stages()

        assertEquals("요청이 몰려 잠시 뒤 다시 봐요", stage(stages, InspectionStage.MODEL).detail, "다시 눌러 보면 되는 실패다")
        assertEquals("모델이 판단을 거부했어요", stage(stages, InspectionStage.TRIAGE).detail)
    }

    @Test
    fun `모르는 사유는 비우지 않고 돌지 않았다고 말한다`() {
        given(InspectionPerspectiveKind.ALL, PerspectiveOutcome.UNCHECKED, skipReason = "NEW_REASON")

        assertEquals(
            NOT_RUN,
            stage(stages(), InspectionStage.MODEL).detail,
            "비우면 화면에 단계 이름만 남아 셀러가 이유를 알 수 없다",
        )
    }

    @Test
    fun `사유가 비어 있어도 돌지 않았다고 말한다`() {
        given(InspectionPerspectiveKind.RULE, PerspectiveOutcome.UNCHECKED, skipReason = null)

        assertEquals(NOT_RUN, stage(stages(), InspectionStage.RULE).detail)
    }

    @Test
    fun `아직 돌고 있으면 행이 없는 단계는 닿지 않았다고 말한다`() {
        given(InspectionPerspectiveKind.RULE, PerspectiveOutcome.CLEAR)

        for (state in listOf(InspectionProcessingState.QUEUED, InspectionProcessingState.IN_FLIGHT)) {
            val stages = stages(state)

            assertEquals(NOT_REACHED, stage(stages, InspectionStage.TRIAGE).detail, "$state 는 더 올 것이 남았다")
            assertEquals(NOT_REACHED, stage(stages, InspectionStage.MODEL).detail, "$state 는 더 올 것이 남았다")
        }
    }

    @Test
    fun `판정이 확정됐으면 행이 없는 단계는 돌지 않았다고 말한다`() {
        // 규칙에 걸려 바로 끝난 반려. TRIAGE·MODEL 은 행이 아예 없고 앞으로도 생기지 않는다
        given(InspectionPerspectiveKind.RULE, PerspectiveOutcome.VIOLATION)

        val stages = stages(InspectionProcessingState.SETTLED)

        assertEquals(NOT_RUN, stage(stages, InspectionStage.TRIAGE).detail, "선별이 꺼진 운영 기본값에서 늘 이 자리다")
        assertEquals(NOT_RUN, stage(stages, InspectionStage.MODEL).detail, "확정된 판정에 '아직'을 적으면 셀러가 오지 않을 결과를 기다린다")
    }

    @Test
    fun `검수를 한 번도 타지 않은 상품은 닿지 않았다고 말한다`() {
        val stages = InspectionStageResult.fold(emptyList(), processingState = null)

        assertEquals(List(3) { NOT_REACHED }, stages.map { it.detail }, "요청을 넣으면 돌 단계다")
    }

    @Test
    fun `행이 없는 단계와 돌지 않은 단계를 다른 문장으로 가른다`() {
        given(InspectionPerspectiveKind.RULE, PerspectiveOutcome.UNCHECKED, skipReason = null)

        val stages = stages(InspectionProcessingState.IN_FLIGHT)

        assertEquals(NOT_RUN, stage(stages, InspectionStage.RULE).detail)
        assertEquals(NOT_REACHED, stage(stages, InspectionStage.TRIAGE).detail)
    }

    @Test
    fun `통과한 단계는 사유를 비운다`() {
        given(InspectionPerspectiveKind.RULE, PerspectiveOutcome.CLEAR, skipReason = "RULE_HIT")

        assertNull(
            stage(stages(), InspectionStage.RULE).detail,
            "통과에 사유를 달면 통과가 미검사처럼 보인다",
        )
    }

    @Test
    fun `다른 검수의 관점 행은 섞지 않는다`() {
        findings.rows += InspectionFinding.of(OTHER_INSPECTION_ID, InspectionPerspectiveKind.RULE, PerspectiveOutcome.VIOLATION)

        assertEquals(
            PerspectiveOutcome.UNCHECKED,
            stage(stages(), InspectionStage.RULE).outcome,
        )
    }

    @Test
    fun `관점 행을 한 번만 읽는다`() {
        given(InspectionPerspectiveKind.RULE, PerspectiveOutcome.CLEAR)

        service.findDetail(InspectionSnapshotFixture.settled(INSPECTION_ID))

        assertEquals(1, findings.reads, "위반과 단계를 따로 부르면 상세 한 번에 같은 SELECT 가 두 번 돈다")
    }

    private fun given(
        perspective: InspectionPerspectiveKind,
        outcome: PerspectiveOutcome,
        skipReason: String? = null,
    ) {
        findings.rows += InspectionFinding.of(INSPECTION_ID, perspective, outcome, skipReason)
    }

    /** 상세 조회는 위반과 단계를 한 번에 돌려준다. 이 테스트는 단계 쪽만 본다 */
    private fun stages(processingState: InspectionProcessingState = InspectionProcessingState.SETTLED): List<InspectionStageResult> =
        service.findDetail(InspectionSnapshotFixture.of(INSPECTION_ID, processingState)).stages

    private fun stage(
        stages: List<InspectionStageResult>,
        stage: InspectionStage,
    ): InspectionStageResult = stages.single { it.stage == stage }

    private class FakeInspectionFindingRepository : InspectionFindingRepository {
        val rows = mutableListOf<InspectionFinding>()
        var reads = 0

        override fun upsert(finding: InspectionFinding): InspectionFinding = error("쓰지 않음")

        override fun findByInspectionId(inspectionId: Long): List<InspectionFinding> {
            reads++
            return rows.filter { it.inspectionId == inspectionId }
        }

        override fun findOne(
            inspectionId: Long,
            perspective: InspectionPerspectiveKind,
        ): InspectionFinding? = error("쓰지 않음")
    }

    private companion object {
        const val INSPECTION_ID = 7L
        const val OTHER_INSPECTION_ID = 8L
        const val NOT_REACHED = "아직 이 단계에 닿지 않았어요"
        const val NOT_RUN = "이 단계는 돌지 않았어요"

        inline fun <reified T : Any> unused(): T =
            Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, _ ->
                error("이 테스트에서 쓰지 않는 의존이 호출됨: ${method.name}")
            } as T
    }
}
