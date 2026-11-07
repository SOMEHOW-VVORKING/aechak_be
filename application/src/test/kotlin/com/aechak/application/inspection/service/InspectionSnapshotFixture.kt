package com.aechak.application.inspection.service

import com.aechak.domain.inspection.enums.InspectionJudgment
import com.aechak.domain.inspection.enums.InspectionProcessingState
import java.time.LocalDateTime

/**
 * 상세 조회에 넣을 검수 스냅샷.
 *
 * 단계 사유를 가르는 축이 [InspectionSnapshot.processingState] 하나라 나머지는 아무 값이어도 된다.
 */
object InspectionSnapshotFixture {
    /** 판정이 확정된 검수 -- 행이 없는 단계는 "돌지 않았다"가 된다 */
    fun settled(inspectionId: Long): InspectionSnapshot = of(inspectionId, InspectionProcessingState.SETTLED)

    fun of(
        inspectionId: Long,
        processingState: InspectionProcessingState,
    ): InspectionSnapshot =
        InspectionSnapshot(
            inspectionId = inspectionId,
            processingState = processingState,
            contentHash = "a".repeat(64),
            judgment = InspectionJudgment.FLAG.takeIf { processingState == InspectionProcessingState.SETTLED },
            reasonCode = null,
            reasonSummary = null,
            sellerNotice = null,
            requestedAt = LocalDateTime.of(2026, 11, 7, 10, 0),
            settledAt = LocalDateTime.of(2026, 11, 7, 10, 1).takeIf { processingState == InspectionProcessingState.SETTLED },
        )
}
