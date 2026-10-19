package com.aechak.application.order.usecase

import com.aechak.application.order.usecase.query.OrderListQuery
import com.aechak.application.order.usecase.result.OrderDetailResult
import com.aechak.application.order.usecase.result.OrderGroupDetailResult
import com.aechak.application.order.usecase.result.OrderListResult

interface OrderQueryUseCase {
    fun getOrders(query: OrderListQuery): OrderListResult

    /** 결제가 끝나지 않았거나 취소된 그룹도 그대로 돌려줌 */
    fun getOrderGroup(
        buyerId: Long,
        orderGroupPublicId: String,
    ): OrderGroupDetailResult

    fun getOrderDetail(
        orderPublicId: String,
        buyerId: Long,
    ): OrderDetailResult
}
