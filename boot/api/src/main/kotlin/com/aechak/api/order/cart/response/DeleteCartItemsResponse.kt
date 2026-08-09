package com.aechak.api.order.cart.response

import com.aechak.application.order.cart.usecase.result.DeleteCartItemsResult

data class DeleteCartItemsResponse(
    val deletedCount: Int,
) {
    companion object {
        fun from(result: DeleteCartItemsResult): DeleteCartItemsResponse =
            DeleteCartItemsResponse(
                deletedCount = result.deletedCount,
            )
    }
}
