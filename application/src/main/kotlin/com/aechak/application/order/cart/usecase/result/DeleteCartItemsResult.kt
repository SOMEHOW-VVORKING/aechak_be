package com.aechak.application.order.cart.usecase.result

data class DeleteCartItemsResult(
    val deletedCount: Int,
    /** 담긴 수량의 합계. 품목 종류 수가 아님. */
    val cartItemCount: Int,
)
