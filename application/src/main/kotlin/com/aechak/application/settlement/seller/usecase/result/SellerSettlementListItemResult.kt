package com.aechak.application.settlement.seller.usecase.result

import com.aechak.application.settlement.port.SellerSettlementStatus
import com.aechak.application.settlement.port.view.SellerSettlementOrderView
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 정산 목록 한 행 — 주문 하나의 요약.
 *
 * [orderId]는 주문의 공개 식별자(ULID)다. [feeAmount]는 플랫폼·결제 수수료의 합이고,
 * [finalAmount]는 정산 행에 저장된 지급 예정액의 합이라 `productAmount - feeAmount`와 같지 않다
 * (배송비는 더해지고 쿠폰 부담·환불 차감은 빠진다).
 */
data class SellerSettlementListItemResult(
    val orderId: String,
    val settlementMonth: String,
    val confirmedAt: LocalDateTime?,
    val expectedPayoutDate: LocalDate,
    val productAmount: Long,
    val feeAmount: Long,
    val finalAmount: Long,
    val status: SellerSettlementStatus,
) {
    companion object {
        fun from(view: SellerSettlementOrderView): SellerSettlementListItemResult =
            SellerSettlementListItemResult(
                orderId = view.orderPublicId,
                settlementMonth = view.settlementMonth,
                confirmedAt = view.confirmedAt,
                expectedPayoutDate = view.expectedPayoutDate,
                productAmount = view.productAmount,
                feeAmount = view.commission + view.pgFee,
                finalAmount = view.netAmount,
                status = SellerSettlementStatus.ofRank(view.statusRank),
            )
    }
}
