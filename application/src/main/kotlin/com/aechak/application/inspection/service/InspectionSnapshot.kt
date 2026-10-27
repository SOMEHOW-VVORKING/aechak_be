package com.aechak.application.inspection.service

import com.aechak.domain.inspection.ContentInspection
import com.aechak.domain.inspection.enums.InspectionJudgment
import com.aechak.domain.inspection.enums.InspectionProcessingState
import java.time.LocalDateTime

/**
 * 읽은 시점의 검수 상태.
 *
 * 엔티티를 그대로 넘기지 않는 이유는 다른 바운디드 컨텍스트가 변경자를 쥐지 않게 하려는 것이다.
 * 노출 상태를 바꾸는 길은 판정 적용 하나여야 한다.
 */
data class InspectionSnapshot(
    /** 검수 행 식별자. 읽은 뒤에 그것이 아직 최신인지 대조하는 데 쓴다 */
    val inspectionId: Long,
    val processingState: InspectionProcessingState,
    val judgment: InspectionJudgment?,
    val reasonCode: String?,
    val reasonSummary: String?,
    val sellerNotice: String?,
    val requestedAt: LocalDateTime,
    val settledAt: LocalDateTime?,
) {
    companion object {
        fun from(inspection: ContentInspection): InspectionSnapshot =
            InspectionSnapshot(
                inspectionId = inspection.id,
                processingState = inspection.processingState,
                judgment = inspection.judgment,
                reasonCode = inspection.reasonCode,
                reasonSummary = inspection.reasonSummary,
                sellerNotice = inspection.sellerNotice,
                requestedAt = inspection.requestedAt,
                settledAt = inspection.settledAt,
            )
    }
}
