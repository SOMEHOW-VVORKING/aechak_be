package com.aechak.application.order.port

data class OrderListCondition(
    val buyerId: Long,
    val filter: OrderStatusFilter,
    val lastId: Long?,
    val limit: Int,
) {
    init {
        require(limit > 0) { "limit은 양수여야 합니다." }
    }
}
