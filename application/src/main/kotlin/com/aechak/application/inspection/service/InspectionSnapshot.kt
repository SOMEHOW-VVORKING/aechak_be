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
    val processingState: InspectionProcessingState,
    /** 이 검수가 본 내용의 지문. 이의가 아직 그 내용을 가리키는지 가르는 축이다 */
    val contentHash: String,
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
                processingState = inspection.processingState,
                contentHash = inspection.contentHash,
                judgment = inspection.judgment,
                reasonCode = inspection.reasonCode,
                reasonSummary = inspection.reasonSummary,
                sellerNotice = inspection.sellerNotice,
                requestedAt = inspection.requestedAt,
                settledAt = inspection.settledAt,
            )
    }
}
