package com.aechak.seller.product.response

import com.aechak.application.product.product.usecase.result.InspectionProgressStep
import com.aechak.application.product.product.usecase.result.SellerProductInspectionResult
import com.aechak.domain.inspection.enums.InspectionJudgment
import com.aechak.domain.inspection.enums.InspectionProcessingState
import com.aechak.domain.product.product.enums.InspectionStatus
import java.time.LocalDateTime

/**
 * 내 상품의 검수 결과.
 *
 * [inspectionStatus]는 상품의 노출 판정이고 [judgment]는 가장 최근 검수의 판정이다.
 * 둘은 어긋날 수 있다 — 노출 게이트가 꺼진 환경에서는 위반 판정이 나도 노출이 그대로다.
 * 검수를 한 번도 타지 않은 상품은 [inspectionStatus]와 [progressStep]만 채워지고 나머지가 비어 온다.
 *
 * [progressStep]: REQUESTED, AI_REVIEWING, ADMIN_REVIEWING, APPROVED, REJECTED.
 * [rejectReason]은 관리자가 적은 반려 사유이고 관리자가 반려한 상품일 때만 온다. AI가 반려했으면 비어 있고
 * 사유는 [reasonSummary]와 [sellerNotice]에 있다.
 *
 * [appealedAt]이 차 있으면 지금 걸린 것에 이의를 접수해 둔 상태다. 이의의 답은 따로 오지 않고
 * [progressStep]으로 온다 — 접수 뒤에는 ADMIN_REVIEWING, 수용되면 APPROVED, 기각되면
 * REJECTED와 [rejectReason]이다.
 *
 * [appealCleared]가 참이면 접수한 이의가 그 뒤 바뀐 내용 때문에 무효가 됐다는 뜻이고 재접수가
 * 열려 있다. [appealedAt]이 빈 이유를 이것으로 가른다 — 없으면 "접수한 적 없음"과 구별되지 않는다.
 */
data class SellerProductInspectionResponse(
    val productId: String,
    val inspectionStatus: InspectionStatus,
    val progressStep: InspectionProgressStep,
    val processingState: InspectionProcessingState?,
    val judgment: InspectionJudgment?,
    val reasonCode: String?,
    val reasonSummary: String?,
    val sellerNotice: String?,
    val rejectReason: String?,
    val appealedAt: LocalDateTime?,
    val appealReason: String?,
    val appealCleared: Boolean,
    val requestedAt: LocalDateTime?,
    val settledAt: LocalDateTime?,
) {
    companion object {
        fun from(result: SellerProductInspectionResult): SellerProductInspectionResponse =
            SellerProductInspectionResponse(
                productId = result.productId,
                inspectionStatus = result.inspectionStatus,
                progressStep = result.progressStep,
                processingState = result.processingState,
                judgment = result.judgment,
                reasonCode = result.reasonCode,
                reasonSummary = result.reasonSummary,
                sellerNotice = result.sellerNotice,
                rejectReason = result.rejectReason,
                appealedAt = result.appealedAt,
                appealReason = result.appealReason,
                appealCleared = result.appealCleared,
                requestedAt = result.requestedAt,
                settledAt = result.settledAt,
            )
    }
}
