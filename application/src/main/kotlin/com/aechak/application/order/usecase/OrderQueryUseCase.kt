package com.aechak.application.order.usecase

import com.aechak.application.order.usecase.query.OrderListQuery
import com.aechak.application.order.usecase.result.OrderDetailResult
import com.aechak.application.order.usecase.result.OrderListResult

interface OrderQueryUseCase {
    fun getOrders(query: OrderListQuery): OrderListResult

    fun getOrderDetail(
        orderPublicId: String,
        buyerId: Long,
    ): OrderDetailResult
}
