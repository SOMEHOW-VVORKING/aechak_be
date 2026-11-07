package com.aechak.application.inspection.usecase.result

import com.aechak.domain.inspection.InspectionFinding
import com.aechak.domain.inspection.enums.InspectionPerspectiveKind
import com.aechak.domain.inspection.enums.InspectionProcessingState
import com.aechak.domain.inspection.enums.PerspectiveOutcome

/** 셀러가 보는 검수 단계. 모델 관점 셋은 MODEL 한 줄로 접힌다 */
enum class InspectionStage {
    RULE,
    TRIAGE,
    MODEL,
}

/**
 * 검수 한 건이 단계마다 무엇을 봤는지.
 *
 * 세 단계를 **항상 같은 순서로 세 줄** 돌려준다. 관점 행이 없는 단계도 UNCHECKED 한 줄로
 * 남아서, 화면이 단계 수를 세지 않고 그린다.
 *
 * 통과와 미검사를 섞지 않는 것이 이 목록의 존재 이유다 -- UNCHECKED 를 CLEAR 로 올리지 않는다.
 * 모델이 보지 못한 것을 "문제 없음"으로 보여 주면 셀러가 통과한 줄 알고 등록한다.
 *
 * 같은 이유로 **"닿지 않음"과 "돌지 않음"도 섞지 않는다**. 판정이 확정된 뒤에 빈 단계를
 * "아직"으로 적으면 더 기다리면 결과가 온다는 뜻이 되는데, 그 단계는 끝내 돌지 않았다.
 *
 * 내부 값(관점 이름·skipReason 원문·본문 위치·확신도)은 싣지 않는다. 셀러가 쓸 수 없는 값이고
 * 운영 축이 셀러 계약으로 새면 되돌릴 수 없다.
 */
data class InspectionStageResult(
    val stage: InspectionStage,
    val outcome: PerspectiveOutcome,
    /** 미검사일 때만 적는다. 위반·통과는 설명할 것이 없어 null */
    val detail: String?,
) {
    companion object {
        /**
         * 관점 행을 단계 셋으로 접는다. 행이 하나도 없으면 세 줄 전부 미검사다.
         *
         * [processingState]는 행이 없는 단계의 사유를 가르는 축이다 -- 검수를 한 번도 타지 않은
         * 상품은 null 을 준다. 이것 없이 접으면 "닿지 않음"과 "돌지 않음"을 구별할 수 없다.
         */
        fun fold(
            findings: List<InspectionFinding>,
            processingState: InspectionProcessingState?,
        ): List<InspectionStageResult> {
            val byPerspective = findings.associateBy { it.perspective }
            return PERSPECTIVES_BY_STAGE.map { (stage, perspectives) ->
                val rows = perspectives.mapNotNull(byPerspective::get)
                val outcome = outcomeOf(rows)
                InspectionStageResult(stage, outcome, detailOf(outcome, rows, processingState))
            }
        }

        /**
         * 접힌 관점 가운데 가장 센 결과를 쓴다.
         *
         * 하나라도 위반이면 위반, 아니고 하나라도 통과면 통과, 전부 미검사면 미검사다.
         * 미검사를 통과 쪽으로 올리면 모델이 못 본 관점이 "문제 없음"으로 보인다.
         */
        private fun outcomeOf(rows: List<InspectionFinding>): PerspectiveOutcome =
            when {
                rows.any { it.outcome == PerspectiveOutcome.VIOLATION } -> PerspectiveOutcome.VIOLATION
                rows.any { it.outcome == PerspectiveOutcome.CLEAR } -> PerspectiveOutcome.CLEAR
                else -> PerspectiveOutcome.UNCHECKED
            }

        /**
         * 미검사일 때만 사유를 적는다.
         *
         * 행이 아예 없는 것과 행은 있는데 돌지 않은 것을 가른다 -- 후자는 돌지 않은 이유가 있다.
         * 모르는 skipReason 은 null 로 비우지 않는다. 비우면 화면에 단계 이름만 남고 셀러는 그
         * 줄이 왜 비었는지 알 수 없다.
         *
         * **행이 없는 단계는 판정이 확정됐는지로 가른다.** 판정이 끝난 검수에 그 단계 행이 없으면
         * 그 단계는 끝내 돌지 않은 것이라, "아직 닿지 않았다"는 거짓이다 -- 선별이 꺼진 운영
         * 기본값에서 TRIAGE 가 늘 그 자리이고, 규칙에 걸려 바로 끝난 반려는 MODEL 도 그 자리다.
         * 셀러는 기다리면 결과가 더 온다고 읽고 기다린다.
         */
        private fun detailOf(
            outcome: PerspectiveOutcome,
            rows: List<InspectionFinding>,
            processingState: InspectionProcessingState?,
        ): String? {
            if (outcome != PerspectiveOutcome.UNCHECKED) {
                return null
            }
            if (rows.isEmpty()) {
                return if (processingState == InspectionProcessingState.SETTLED) NOT_RUN else NOT_REACHED
            }
            return DETAIL_BY_SKIP_REASON[rows.firstNotNullOfOrNull { it.skipReason }] ?: NOT_RUN
        }

        /** 단계마다 접히는 관점. 이 순서가 MODEL 의 사유 고르는 순서이기도 하다 */
        private val PERSPECTIVES_BY_STAGE =
            linkedMapOf(
                InspectionStage.RULE to listOf(InspectionPerspectiveKind.RULE),
                InspectionStage.TRIAGE to listOf(InspectionPerspectiveKind.TRIAGE),
                InspectionStage.MODEL to
                    listOf(
                        InspectionPerspectiveKind.ALL,
                        InspectionPerspectiveKind.DISPLAY_ADS,
                        InspectionPerspectiveKind.CONDUCT_TRADE,
                    ),
            )

        private const val NOT_REACHED = "아직 이 단계에 닿지 않았어요"
        private const val NOT_RUN = "이 단계는 돌지 않았어요"

        /** skipReason 을 셀러가 읽는 문장으로 옮긴다. 이 표가 유일한 사본이다 */
        private val DETAIL_BY_SKIP_REASON =
            mapOf(
                "RULE_HIT" to "앞 단계에서 이미 걸려 더 보지 않았어요",
                "TRIAGE_CLEAR" to "1차 선별이 깊은 검수가 필요하지 않다고 봤어요",
                "TRIAGE_CERTAIN" to "1차 선별이 위반을 확정했어요",
                "PROMOTED" to "1차 선별이 더 보자고 넘겼어요",
                "TRIAGE_DISABLED" to "선별을 쓰지 않는 설정이에요",
                "LLM_DISABLED" to "모델 검수를 쓰지 않는 설정이에요",
                "WAIT_LIMIT_EXCEEDED" to "기한 안에 검수가 끝나지 않았어요",
                "EVIDENCE_UNVERIFIED" to "모델이 든 근거를 본문에서 찾지 못해 판단을 버렸어요",
                "SCHEMA_INVALID" to "모델 응답이 계약과 달라 판단을 버렸어요",
                "TIMEOUT" to "모델 호출이 시간 안에 끝나지 않았어요",
                "RATE_LIMITED" to "요청이 몰려 잠시 뒤 다시 봐요",
                "REFUSED" to "모델이 판단을 거부했어요",
                "BUDGET_EXHAUSTED" to "예산 한도에 걸려 호출하지 못했어요",
                "TRUNCATED" to "모델 응답이 중간에 끊겼어요",
                "ERROR" to "모델 호출이 실패했어요",
            )
    }
}
