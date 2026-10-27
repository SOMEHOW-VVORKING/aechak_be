package com.aechak.application.settlement.seller.usecase.result

import com.aechak.application.settlement.port.view.SellerSettlementItemView
import com.aechak.application.settlement.port.view.SellerSettlementOrderView
import java.math.BigDecimal

/**
 * 정산 상세 — 목록과 같은 요약에 금액 분해와 품목 내역을 더한 것.
 *
 * 요율은 정산 행에 박힌 생성 시점 스냅샷이다. 화면이 "플랫폼 수수료 8%"를 하드코딩하지 않도록 내려 주며,
 * 정책이 바뀌어도 과거 정산의 수수료 근거는 이 값이다.
 */
data class SellerSettlementDetailResult(
    val summary: SellerSettlementListItemResult,
    val shippingFee: Long,
    val couponBurden: Long,
    val platformFeeAmount: Long,
    val pgFeeAmount: Long,
    val refundDeduction: Long,
    val platformFeeRate: BigDecimal,
    val pgFeeRate: BigDecimal,
    val items: List<SellerSettlementItemResult>,
) {
    companion object {
        fun from(
            view: SellerSettlementOrderView,
            items: List<SellerSettlementItemView>,
        ): SellerSettlementDetailResult =
            SellerSettlementDetailResult(
                summary = SellerSettlementListItemResult.from(view),
                shippingFee = view.shippingFee,
                couponBurden = view.sellerCouponBurden,
                platformFeeAmount = view.commission,
                pgFeeAmount = view.pgFee,
                refundDeduction = view.refundDeduction,
                platformFeeRate = view.platformFeeRate,
                pgFeeRate = view.pgFeeRate,
                items = items.map(SellerSettlementItemResult::from),
            )
    }
}
