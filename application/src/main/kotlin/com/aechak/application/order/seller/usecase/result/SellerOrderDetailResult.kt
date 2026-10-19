package com.aechak.application.order.seller.usecase.result

import com.aechak.application.order.seller.port.view.SellerOrderLineView

/** 셀러 주문 상세 — 주문 단위 값은 목록 행과 같고 품목이 더 붙는다. */
data class SellerOrderDetailResult(
    val order: SellerOrderSummaryResult,
    val items: List<SellerOrderLineResult>,
)

data class SellerOrderLineResult(
    val orderItemId: Long,
    val productId: String,
    val productName: String,
    val thumbnailUrl: String?,
    val optionName: String,
    val quantity: Int,
    val unitPrice: Long,
    val discountAllocatedAmount: Long,
    val itemStatus: String,
) {
    companion object {
        fun of(
            view: SellerOrderLineView,
            thumbnailUrl: String?,
        ): SellerOrderLineResult =
            SellerOrderLineResult(
                orderItemId = view.orderItemId,
                productId = view.productPublicId,
                productName = view.productName,
                thumbnailUrl = thumbnailUrl,
                optionName = view.optionName,
                quantity = view.quantity,
                unitPrice = view.unitPrice,
                discountAllocatedAmount = view.discountAllocatedAmount,
                itemStatus = view.itemStatus.name,
            )
    }
}
