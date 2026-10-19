package com.aechak.application.order.facade

import com.aechak.application.file.usecase.FileUseCase
import com.aechak.application.order.service.OrderGroupService
import com.aechak.application.order.service.OrderQueryService
import com.aechak.application.order.service.OrderService
import com.aechak.application.order.usecase.OrderQueryUseCase
import com.aechak.application.order.usecase.query.OrderListQuery
import com.aechak.application.order.usecase.result.OrderDetailResult
import com.aechak.application.order.usecase.result.OrderGroupDetailResult
import com.aechak.application.order.usecase.result.OrderGroupItemResult
import com.aechak.application.order.usecase.result.OrderListResult
import com.aechak.application.order.usecase.result.SellerOrderItemResult
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class OrderQueryFacade(
    private val orderService: OrderService,
    private val orderGroupService: OrderGroupService,
    private val orderQueryService: OrderQueryService,
    private val fileUseCase: FileUseCase,
) : OrderQueryUseCase {
    @Transactional(readOnly = true)
    override fun getOrders(query: OrderListQuery): OrderListResult {
        val groupPage = orderQueryService.getGroupPage(query)
        val sellerOrdersByGroupId = orderQueryService.getSellerOrders(groupPage.items.map { it.id }).groupBy { it.orderGroupId }

        val linesByOrderId =
            orderQueryService
                .getLines(sellerOrdersByGroupId.values.flatten().map { it.orderId })
                .groupBy { it.orderId }

        val page =
            groupPage.map { group ->
                OrderGroupItemResult.of(
                    view = group,
                    sellerOrders =
                        sellerOrdersByGroupId[group.id].orEmpty().map { sellerOrder ->
                            SellerOrderItemResult.of(
                                view = sellerOrder,
                                lines = linesByOrderId[sellerOrder.orderId].orEmpty(),
                                resolveThumbnail = fileUseCase::resolveMediaUrl,
                            )
                        },
                )
            }
        return OrderListResult(page = page)
    }

    @Transactional(readOnly = true)
    override fun getOrderGroup(
        buyerId: Long,
        orderGroupPublicId: String,
    ): OrderGroupDetailResult {
        val orderGroup = orderGroupService.getOwnedOrderGroup(buyerId, orderGroupPublicId)
        val orders = orderService.findOrders(orderGroup.id)
        return OrderGroupDetailResult.of(
            orderGroup = orderGroup,
            orders = orders,
            lines = orderQueryService.getLines(orders.map { it.id }),
            deliveryAddress = orderGroupService.decryptDeliveryAddress(orderGroup.deliveryAddress),
            resolveThumbnail = fileUseCase::resolveMediaUrl,
        )
    }

    @Transactional(readOnly = true)
    override fun getOrderDetail(
        orderPublicId: String,
        buyerId: Long,
    ): OrderDetailResult {
        val detail = orderQueryService.getOwnedDetail(orderPublicId, buyerId)
        return OrderDetailResult.of(
            view = detail,
            lines = orderQueryService.getLines(listOf(detail.orderId)),
            resolveThumbnail = fileUseCase::resolveMediaUrl,
        )
    }
}
