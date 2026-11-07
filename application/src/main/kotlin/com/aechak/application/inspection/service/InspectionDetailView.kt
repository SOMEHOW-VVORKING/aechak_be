package com.aechak.application.inspection.service

import com.aechak.application.inspection.port.view.ViolationView
import com.aechak.application.inspection.usecase.result.InspectionStageResult

/**
 * 검수 한 건을 한 번 읽어 얻은 두 투영.
 *
 * 둘을 함께 돌려주는 것은 질의 수 때문이다. 셀러 검수 상세가 둘을 늘 같이 쓰므로 따로 열면
 * 같은 SELECT 가 요청마다 두 번 돈다.
 */
data class InspectionDetailView(
    /** 글 안의 위치 순서로 합친 위반 목록. 없으면 빈 목록 */
    val violations: List<ViolationView>,
    /** RULE·TRIAGE·MODEL 순서로 **항상 세 줄** */
    val stages: List<InspectionStageResult>,
)
