package com.aechak.application.order.usecase.query

import com.aechak.application.order.port.OrderStatusFilter

data class OrderListQuery(
    val buyerId: Long,
    val filter: OrderStatusFilter = OrderStatusFilter.ALL,
    val cursor: String? = null,
    val size: Int = DEFAULT_SIZE,
) {
    init {
        require(size in SIZE_MIN..SIZE_MAX) { "size는 $SIZE_MIN~$SIZE_MAX 범위 안에 있어야 합니다." }
    }

    companion object {
        const val DEFAULT_SIZE = 20

        const val SIZE_MIN = 1L
        const val SIZE_MAX = 100L
    }
}
