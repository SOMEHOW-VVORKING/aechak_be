package com.aechak.seller.settlement.response

import com.aechak.application.settlement.seller.usecase.result.SellerSettlementItemResult

/**
 * 정산 상세의 품목 한 줄. 금액은 정산 행에 저장된 값 그대로다.
 * productName·quantity는 주문품목·상품 버전 스냅샷이 없으면 비어 있다.
 */
data class SellerSettlementItemResponse(
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
        fun from(result: SellerSettlementItemResult): SellerSettlementItemResponse =
            SellerSettlementItemResponse(
                orderItemId = result.orderItemId,
                productName = result.productName,
                quantity = result.quantity,
                grossAmount = result.grossAmount,
                shippingFee = result.shippingFee,
                commission = result.commission,
                pgFee = result.pgFee,
                sellerCouponBurden = result.sellerCouponBurden,
                refundDeduction = result.refundDeduction,
                netAmount = result.netAmount,
            )
    }
}
