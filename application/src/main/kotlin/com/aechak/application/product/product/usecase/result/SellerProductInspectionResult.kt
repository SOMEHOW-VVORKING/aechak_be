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
 * **사유 셋([reasonCode]·[reasonSummary]·[sellerNotice])은 지금 열린 검토 행을 막고 있는
 * 판정에서 읽는다.** [judgment]와 진행 시각은 가장 최근 검수의 것이라 둘이 어긋날 수 있다 —
 * 예를 들어 판정이 실패한 뒤에는 `judgment`가 실패인데 사유는 그 전의 위반 판정 것이다.
 * 가장 최근 검수에서 사유를 읽으면 모델이 답을 못 줄 때마다 반려는 남고 사유만 비어서,
 * 셀러는 무엇을 고쳐야 하는지 알 수 없게 된다.
 *
 * 이의의 결과를 따로 싣지 않는다. [progressStep]이 접수 뒤 ADMIN_REVIEWING이 되고
 * ([appealedAt]이 붙는 행이 곧 열린 검토 행이다), 답은 그 행이 닫히는 방식으로 나온다 —
 * 수용은 APPROVED, 기각은 REJECTED와 [rejectReason]이다. 관리자 내부 열거를 셀러 계약에
 * 새게 할 이유가 없다.
 *
 * 접수 사실 자체는 [appealedAt]·[appealReason]·[appealCleared]로 싣는다. AI가 반려한 상품은
 * 노출 판정이 REJECTED라 접수 전후가 상태만으로는 구별되지 않고, 셀러가 자기 이의가 들어갔는지
 * 확인할 길이 그 셋뿐이다.
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
    /** 관리자가 반려한 상품일 때만 채움. AI가 반려했으면 null이고 사유는 [reasonSummary]와 [sellerNotice]에 있음 */
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
        /**
         * [snapshot]이 null이면 한 번도 검수를 타지 않은 상품이다. 오류로 다루지 않는다.
         *
         * [blocking]은 지금 열린 검토 행을 막고 있는 판정이다. 되찾지 못하면(행이 없거나
         * 이의가 접수 시각으로 연 행이면) [snapshot]으로 떨어진다.
         */
        fun of(
            product: Product,
            snapshot: InspectionSnapshot?,
            openReview: OpenProductReviewSnapshot?,
            blocking: InspectionSnapshot?,
            adminRejectReason: String?,
        ): SellerProductInspectionResult {
            val blame = blocking ?: snapshot
            return SellerProductInspectionResult(
                productId = product.publicId,
                inspectionStatus = product.inspectionStatus,
                progressStep = progressStepOf(product.inspectionStatus, snapshot, openReview, blame),
                processingState = snapshot?.processingState,
                judgment = snapshot?.judgment,
                reasonCode = blame?.reasonCode,
                reasonSummary = blame?.reasonSummary,
                sellerNotice = blame?.sellerNotice,
                rejectReason = adminRejectReason.takeIf { product.inspectionStatus == InspectionStatus.REJECTED },
                appealedAt = openReview?.appealedAt,
                appealReason = openReview?.appealReason,
                appealCleared = openReview?.appealCleared == true,
                requestedAt = snapshot?.requestedAt,
                settledAt = snapshot?.settledAt,
            )
        }

        /**
         * 진행 중인 검수, 이의 접수, 열린 검토 행, 상품 상태 순으로 봄. FE는 큐를 볼 수 없어 여기서 계산함.
         *
         * 열린 행이 있을 때 **그 행을 막고 있는 판정**([blame])으로 가름. 위반이 막고 있으면 AI
         * 반려라 셀러에게 반려로 보이고, 통과가 막고 있으면 관리자가 반려한 상품을 고쳐 통과한
         * 것이라 관리자 확인이다. 노출 상태를 분기자로 쓰지 않는 이유가 노출 게이트 스위치임 —
         * 꺼진 환경에서는 위반 판정이 나도 상품이 APPROVED로 남아서, 상태로 가르면 AI가 위반을
         * 찾은 상품이 셀러에게 "승인"으로 보인다.
         *
         * 가장 최근 검수의 판정으로 가르면 안 됨. 모델이 답을 못 준 판정은 상품도 큐도 건드리지
         * 않는데, 그 통과를 분기자로 쓰면 아무것도 등재·갱신되지 않았는데 셀러에게 "관리자가
         * 보고 있다"고 말한다.
         *
         * 이의 접수를 먼저 보는 이유가 접수 확인임. 접수해도 열린 행의 판정이 위반이라 화면이
         * 반려인 채면 셀러는 접수가 됐는지 알 수 없고, 같은 이의를 다시 보내려다 중복으로 막힘.
         */
        private fun progressStepOf(
            inspectionStatus: InspectionStatus,
            snapshot: InspectionSnapshot?,
            openReview: OpenProductReviewSnapshot?,
            blame: InspectionSnapshot?,
        ): InspectionProgressStep =
            when {
                snapshot?.processingState == InspectionProcessingState.QUEUED -> InspectionProgressStep.REQUESTED
                snapshot?.processingState == InspectionProcessingState.IN_FLIGHT -> InspectionProgressStep.AI_REVIEWING
                openReview?.appealedAt != null -> InspectionProgressStep.ADMIN_REVIEWING
                openReview != null && blame?.judgment == InspectionJudgment.FLAG -> InspectionProgressStep.REJECTED
                openReview != null -> InspectionProgressStep.ADMIN_REVIEWING
                inspectionStatus == InspectionStatus.APPROVED -> InspectionProgressStep.APPROVED
                inspectionStatus == InspectionStatus.REJECTED -> InspectionProgressStep.REJECTED
                else -> InspectionProgressStep.REQUESTED
            }
    }
}
