package com.aechak.application.product.product.usecase.result

import com.aechak.application.product.product.port.view.SellerProductStatusCountView

data class SellerProductStatusCountsResult(
    val awaitingInspection: Long,
    val rejected: Long,
    val suspended: Long,
    val soldOut: Long,
) {
    companion object {
        fun from(view: SellerProductStatusCountView): SellerProductStatusCountsResult =
            SellerProductStatusCountsResult(
                awaitingInspection = view.awaitingInspection,
                rejected = view.rejected,
                suspended = view.suspended,
                soldOut = view.soldOut,
            )
    }
}
