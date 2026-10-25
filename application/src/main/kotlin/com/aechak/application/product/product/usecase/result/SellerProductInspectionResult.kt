package com.aechak.application.product.product.usecase.result

import com.aechak.application.inspection.service.InspectionSnapshot
import com.aechak.application.inspection.service.OpenProductReviewSnapshot
import com.aechak.domain.inspection.enums.InspectionJudgment
import com.aechak.domain.inspection.enums.InspectionProcessingState
import com.aechak.domain.product.product.Product
import com.aechak.domain.product.product.enums.InspectionStatus
import java.time.LocalDateTime

/**
 * 셀러가 보는 내 상품의 검수 결과.
 *
 * 상품의 노출 판정([inspectionStatus])과 가장 최근 검수의 판정([judgment])을 함께 준다.
 * 둘은 어긋날 수 있다. 노출 게이트가 꺼진 환경에서는 위반 판정이 나도 노출이 그대로이고,
 * 판정을 받지 못한 건은 이미 보이는 상품을 숨기지 않는다.
 *
 * [reasonSummary]는 걸린 구절을 그대로 담는다. 셀러가 직접 쓴 문장이고, 어디를 고쳐야
 * 하는지 알려 주는 것이 반려 통지의 목적이다.
 *
 * 이의 상태를 따로 싣지 않는다. [progressStep]이 접수 뒤 자동으로 ADMIN_REVIEWING이 되고
 * ([appealedAt]이 붙는 행이 곧 열린 검토 행이다), 답은 그 행이 닫히는 방식으로 나온다 —
 * 수용은 APPROVED, 기각은 REJECTED와 [rejectReason]이다. 관리자 내부 열거를 셀러 계약에
 * 새게 할 이유가 없다.
 */
data class SellerProductInspectionResult(
    val productId: String,
    val inspectionStatus: InspectionStatus,
    val progressStep: InspectionProgressStep,
    val processingState: InspectionProcessingState?,
    val judgment: InspectionJudgment?,
    val reasonCode: String?,
    val reasonSummary: String?,
    val sellerNotice: String?,
    /** [inspectionStatus]가 REJECTED일 때만 채움 */
    val rejectReason: String?,
    /** 이의를 접수한 시각. null이면 지금 걸린 것에 이의가 없다 */
    val appealedAt: LocalDateTime?,
    /** 셀러가 접수할 때 적은 문장 */
    val appealReason: String?,
    /**
     * 접수한 이의가 내용이 바뀌어 무효가 됐다. 재접수가 열려 있다는 뜻이다.
     *
     * [appealedAt]이 빈 이유를 가른다 — 이것이 없으면 "접수한 적 없음"과 "무효가 됨"이 같은
     * 모양으로 나가서, 셀러는 접수가 실패했다고 읽는다.
     */
    val appealCleared: Boolean,
    val requestedAt: LocalDateTime?,
    val settledAt: LocalDateTime?,
) {
    companion object {
        /** [snapshot]이 null이면 한 번도 검수를 타지 않은 상품이다. 오류로 다루지 않는다 */
        fun of(
            product: Product,
            snapshot: InspectionSnapshot?,
            openReview: OpenProductReviewSnapshot?,
            latestRejectReason: String?,
        ): SellerProductInspectionResult =
            SellerProductInspectionResult(
                productId = product.publicId,
                inspectionStatus = product.inspectionStatus,
                progressStep = progressStepOf(product.inspectionStatus, snapshot?.processingState, openReview != null),
                processingState = snapshot?.processingState,
                judgment = snapshot?.judgment,
                reasonCode = snapshot?.reasonCode,
                reasonSummary = snapshot?.reasonSummary,
                sellerNotice = snapshot?.sellerNotice,
                rejectReason = latestRejectReason.takeIf { product.inspectionStatus == InspectionStatus.REJECTED },
                appealedAt = openReview?.appealedAt,
                appealReason = openReview?.appealReason,
                appealCleared = openReview?.appealCleared == true,
                requestedAt = snapshot?.requestedAt,
                settledAt = snapshot?.settledAt,
            )

        /** 진행 중인 검수, 관리자 검토 큐, 상품 상태 순으로 봄. FE는 큐를 볼 수 없어 여기서 계산함 */
        private fun progressStepOf(
            inspectionStatus: InspectionStatus,
            processingState: InspectionProcessingState?,
            awaitingAdminReview: Boolean,
        ): InspectionProgressStep =
            when {
                processingState == InspectionProcessingState.QUEUED -> InspectionProgressStep.REQUESTED
                processingState == InspectionProcessingState.IN_FLIGHT -> InspectionProgressStep.AI_REVIEWING
                awaitingAdminReview -> InspectionProgressStep.ADMIN_REVIEWING
                inspectionStatus == InspectionStatus.APPROVED -> InspectionProgressStep.APPROVED
                inspectionStatus == InspectionStatus.REJECTED -> InspectionProgressStep.REJECTED
                else -> InspectionProgressStep.REQUESTED
            }
    }
}
