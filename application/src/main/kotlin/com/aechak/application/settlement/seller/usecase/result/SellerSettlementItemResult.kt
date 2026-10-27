package com.aechak.application.settlement.seller.usecase.result

import com.aechak.application.settlement.port.view.SellerSettlementItemView

/**
 * 정산 상세의 품목 한 줄. 이름·수량은 스냅샷이 없으면 비어 있다 — 금액은 그래도 내려간다.
 * 금액은 정산 행에 저장된 값 그대로이며 `netAmount = grossAmount + shippingFee - commission - pgFee
 * - sellerCouponBurden - refundDeduction` 이 줄마다 성립한다.
 */
data class SellerSettlementItemResult(
    val orderItemId: Long,
    val productName: String?,
    val quantity: Int?,
    val grossAmount: Long,
    val shippingFee: Long,
    val commission: Long,
    val pgFee: Long,
    val sellerCouponBurden: Long,
    val refundDeduction: Long,
    val netAmount: Long,
) {
    companion object {
        fun from(view: SellerSettlementItemView): SellerSettlementItemResult =
            SellerSettlementItemResult(
                orderItemId = view.orderItemId,
                productName = view.productName,
                quantity = view.quantity,
                grossAmount = view.grossAmount,
                shippingFee = view.shippingFee,
                commission = view.commission,
                pgFee = view.pgFee,
                sellerCouponBurden = view.sellerCouponBurden,
                refundDeduction = view.refundDeduction,
                netAmount = view.netAmount,
            )
    }
}
