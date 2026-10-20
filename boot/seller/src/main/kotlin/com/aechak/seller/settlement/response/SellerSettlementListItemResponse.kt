package com.aechak.seller.settlement.response

import com.aechak.application.settlement.seller.usecase.result.SellerSettlementListItemResult
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 정산 목록 한 행. orderId는 주문 공개 식별자(ULID)다.
 * status는 화면 어휘 소문자 — pending / scheduled / completed / held.
 */
data class SellerSettlementListItemResponse(
    val orderId: String,
    val settlementMonth: String,
    val confirmedAt: LocalDateTime?,
    val expectedPayoutDate: LocalDate,
    val productAmount: Long,
    val feeAmount: Long,
    val finalAmount: Long,
    @field:Schema(allowableValues = ["pending", "scheduled", "completed", "held"])
    val status: String,
) {
    companion object {
        fun from(result: SellerSettlementListItemResult): SellerSettlementListItemResponse =
            SellerSettlementListItemResponse(
                orderId = result.orderId,
                settlementMonth = result.settlementMonth,
                confirmedAt = result.confirmedAt,
                expectedPayoutDate = result.expectedPayoutDate,
                productAmount = result.productAmount,
                feeAmount = result.feeAmount,
                finalAmount = result.finalAmount,
                status = result.status.name.lowercase(),
            )
    }
}
