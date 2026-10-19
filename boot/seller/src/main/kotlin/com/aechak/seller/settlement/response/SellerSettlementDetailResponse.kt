package com.aechak.seller.settlement.response

import com.aechak.application.settlement.seller.usecase.result.SellerSettlementDetailResult
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 정산 상세 — 목록과 같은 요약 + 금액 분해 + 품목 내역.
 *
 * platformFeeRate·pgFeeRate는 정산 행에 박힌 생성 시점 스냅샷이다(화면이 요율을 하드코딩하지 않게 내려 준다).
 * finalAmount는 저장된 지급 예정액의 합이라 배송비가 더해지고 쿠폰 부담·환불 차감이 빠진 값이다 —
 * productAmount - feeAmount와 같지 않다.
 */
data class SellerSettlementDetailResponse(
    val orderId: String,
    val settlementMonth: String,
    val confirmedAt: LocalDateTime?,
    val expectedPayoutDate: LocalDate,
    val productAmount: Long,
    val feeAmount: Long,
    val finalAmount: Long,
    val status: String,
    val shippingFee: Long,
    val couponBurden: Long,
    val platformFeeAmount: Long,
    val pgFeeAmount: Long,
    val refundDeduction: Long,
    val platformFeeRate: BigDecimal,
    val pgFeeRate: BigDecimal,
    val items: List<SellerSettlementItemResponse>,
) {
    companion object {
        fun from(result: SellerSettlementDetailResult): SellerSettlementDetailResponse {
            val summary = SellerSettlementSummaryResponse.from(result.summary)
            return SellerSettlementDetailResponse(
                orderId = summary.orderId,
                settlementMonth = summary.settlementMonth,
                confirmedAt = summary.confirmedAt,
                expectedPayoutDate = summary.expectedPayoutDate,
                productAmount = summary.productAmount,
                feeAmount = summary.feeAmount,
                finalAmount = summary.finalAmount,
                status = summary.status,
                shippingFee = result.shippingFee,
                couponBurden = result.couponBurden,
                platformFeeAmount = result.platformFeeAmount,
                pgFeeAmount = result.pgFeeAmount,
                refundDeduction = result.refundDeduction,
                platformFeeRate = result.platformFeeRate,
                pgFeeRate = result.pgFeeRate,
                items = result.items.map(SellerSettlementItemResponse::from),
            )
        }
    }
}
