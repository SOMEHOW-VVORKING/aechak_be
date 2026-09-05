package com.aechak.api.order.request

import com.aechak.application.order.port.OrderStatusFilter
import com.aechak.application.order.usecase.query.OrderListQuery
import org.hibernate.validator.constraints.Range

data class OrderListRequest(
    val status: String = OrderStatusFilter.ALL.code,
    val cursor: String? = null,
    @field:Range(
        min = OrderListQuery.SIZE_MIN,
        max = OrderListQuery.SIZE_MAX,
        message = "size는 {min}~{max} 사이여야 합니다.",
    )
    val size: Int = OrderListQuery.DEFAULT_SIZE,
) {
    fun toQuery(buyerId: Long) =
        OrderListQuery(
            buyerId = buyerId,
            filter = OrderStatusFilter.from(status),
            cursor = cursor,
            size = size,
        )
}
