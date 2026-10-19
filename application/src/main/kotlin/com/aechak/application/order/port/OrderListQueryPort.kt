package com.aechak.application.order.port

import com.aechak.application.order.port.view.OrderActiveClaimView
import com.aechak.application.order.port.view.OrderDetailView
import com.aechak.application.order.port.view.OrderGroupView
import com.aechak.application.order.port.view.OrderLineView
import com.aechak.application.order.port.view.OrderShipmentView
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

    /** 교환 재발송까지 고려해 가장 나중에 등록된 운송장 한 건. */
    fun findLatestShipment(orderId: Long): OrderShipmentView?

    /** 주문품목을 붙잡고 있고 아직 종결되지 않은 클레임 중 가장 나중 한 건. */
    fun findActiveClaim(orderId: Long): OrderActiveClaimView?
}
