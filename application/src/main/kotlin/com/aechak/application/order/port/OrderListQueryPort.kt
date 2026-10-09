package com.aechak.application.order.port

import com.aechak.application.order.port.view.OrderDetailView
import com.aechak.application.order.port.view.OrderGroupView
import com.aechak.application.order.port.view.OrderLineView
import com.aechak.application.order.port.view.SellerOrderView

interface OrderListQueryPort {
    fun findGroupPage(condition: OrderListCondition): List<OrderGroupView>

    fun countGroups(
        buyerId: Long,
        filter: OrderStatusFilter,
    ): Long

    fun findSellerOrdersByGroupIds(groupIds: Collection<Long>): List<SellerOrderView>

    fun findLinesByOrderIds(orderIds: Collection<Long>): List<OrderLineView>

    fun findOwnedDetail(
        orderPublicId: String,
        buyerId: Long,
    ): OrderDetailView?
}
