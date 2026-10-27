package com.aechak.seller.product.response

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
 * 검수를 한 번도 타지 않은 상품은 [inspectionStatus]만 채워지고 나머지가 비어 온다.
 */
data class SellerProductInspectionResponse(
    val productId: String,
    val inspectionStatus: InspectionStatus,
    val processingState: InspectionProcessingState?,
    val judgment: InspectionJudgment?,
    val reasonCode: String?,
    val reasonSummary: String?,
    val sellerNotice: String?,
    val requestedAt: LocalDateTime?,
    val settledAt: LocalDateTime?,
) {
    companion object {
        fun from(result: SellerProductInspectionResult): SellerProductInspectionResponse =
            SellerProductInspectionResponse(
                productId = result.productId,
                inspectionStatus = result.inspectionStatus,
                processingState = result.processingState,
                judgment = result.judgment,
                reasonCode = result.reasonCode,
                reasonSummary = result.reasonSummary,
                sellerNotice = result.sellerNotice,
                requestedAt = result.requestedAt,
                settledAt = result.settledAt,
            )
    }
}
