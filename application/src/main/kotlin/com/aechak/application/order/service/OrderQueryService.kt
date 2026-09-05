package com.aechak.application.order.service

import com.aechak.application.order.port.OrderListCondition
import com.aechak.application.order.port.OrderListQueryPort
import com.aechak.application.order.port.OrderStatusFilter
import com.aechak.application.order.port.view.OrderDetailView
import com.aechak.application.order.port.view.OrderGroupView
import com.aechak.application.order.port.view.OrderLineView
import com.aechak.application.order.port.view.SellerOrderView
import com.aechak.application.order.support.OrderCursorCodec
import com.aechak.application.order.usecase.query.OrderListQuery
import com.aechak.application.support.CursorPageResult
import com.aechak.common.error.BusinessException
import com.aechak.common.error.CommonErrorCode
import com.aechak.domain.order.error.OrderErrorCode
import org.springframework.stereotype.Service

@Service
class OrderQueryService(
    private val orderListQueryPort: OrderListQueryPort,
) {
    fun getGroupPage(query: OrderListQuery): CursorPageResult<OrderGroupView> {
        val lastId = query.cursor?.let { resolveCursor(it, query.filter) }
        val fetched =
            orderListQueryPort.findGroupPage(
                OrderListCondition(
                    buyerId = query.buyerId,
                    filter = query.filter,
                    lastId = lastId,
                    limit = query.size + 1,
                ),
            )
        val hasNext = fetched.size > query.size
        val page = if (hasNext) fetched.take(query.size) else fetched
        return CursorPageResult(
            items = page,
            totalCount = if (query.cursor == null) orderListQueryPort.countGroups(query.buyerId, query.filter) else null,
            nextCursor = if (hasNext) OrderCursorCodec.encode(query.filter, page.last().id) else null,
            hasNext = hasNext,
        )
    }

    fun getSellerOrders(groupIds: Collection<Long>): List<SellerOrderView> = orderListQueryPort.findSellerOrdersByGroupIds(groupIds)

    fun getLines(orderIds: Collection<Long>): List<OrderLineView> = orderListQueryPort.findLinesByOrderIds(orderIds)

    /** 주문 번호 존재 여부를 노출하지 않도록 ORDER_NOT_FOUND로 통일 */
    fun getOwnedDetail(
        orderPublicId: String,
        buyerId: Long,
    ): OrderDetailView =
        orderListQueryPort.findOwnedDetail(orderPublicId, buyerId)
            ?: throw BusinessException(OrderErrorCode.ORDER_NOT_FOUND)

    private fun resolveCursor(
        raw: String,
        filter: OrderStatusFilter,
    ): Long {
        val decoded = OrderCursorCodec.decode(raw)
        if (decoded.filter != filter) throw BusinessException(CommonErrorCode.INVALID_CURSOR)
        return decoded.lastId
    }
}
