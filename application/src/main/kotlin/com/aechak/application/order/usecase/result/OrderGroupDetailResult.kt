package com.aechak.application.order.usecase.result

import com.aechak.application.order.port.view.OrderLineView
import com.aechak.domain.order.group.OrderGroup
import com.aechak.domain.order.group.enums.OrderGroupStatus
import com.aechak.domain.order.order.Order
import com.aechak.domain.order.order.enums.OrderStatus
import java.time.LocalDateTime

data class OrderGroupDetailResult(
    val orderGroupId: String,
    val status: OrderGroupStatus,
    val totalProductAmount: Long,
    val totalShippingFee: Long,
    val usedPoint: Long,
    val finalPaymentAmount: Long,
    val expiresAt: LocalDateTime,
    val deliveryAddress: DeliveryAddressSnapshotResult,
    val orders: List<SellerOrderResult>,
) {
    data class DeliveryAddressSnapshotResult(
        val receiverName: String,
        val contactNumber: String,
        val zipCode: String,
        val baseAddress: String,
        val detailAddress: String?,
        val deliveryMemo: String?,
    )

    data class SellerOrderResult(
        val orderId: String,
        val status: OrderStatus,
        val sellerName: String?,
        val sellerShippingFee: Long,
        val items: List<OrderLineItemResult>,
    )

    companion object {
        fun of(
            orderGroup: OrderGroup,
            orders: List<Order>,
            lines: List<OrderLineView>,
            deliveryAddress: DeliveryAddressSnapshotResult,
            resolveThumbnail: (String?) -> String?,
        ): OrderGroupDetailResult {
            val linesByOrderId = lines.groupBy { it.orderId }
            return OrderGroupDetailResult(
                orderGroupId = orderGroup.publicId,
                status = orderGroup.status,
                totalProductAmount = orderGroup.totalProductAmount,
                totalShippingFee = orderGroup.totalShippingFee,
                usedPoint = orderGroup.usedPoint,
                finalPaymentAmount = orderGroup.finalPaymentAmount,
                expiresAt = requireNotNull(orderGroup.expiresAt) { "주문그룹 상세 조회 결과에는 만료 시각이 필수입니다 (publicId=${orderGroup.publicId})" },
                deliveryAddress = deliveryAddress,
                orders = orders.map { toOrder(it, linesByOrderId[it.id].orEmpty(), resolveThumbnail) },
            )
        }

        private fun toOrder(
            order: Order,
            lines: List<OrderLineView>,
            resolveThumbnail: (String?) -> String?,
        ): SellerOrderResult =
            SellerOrderResult(
                orderId = order.publicId,
                status = order.status,
                sellerName = order.sellerNameSnapshot,
                sellerShippingFee = order.sellerShippingFee,
                items = lines.map { OrderLineItemResult.of(it, resolveThumbnail(it.thumbnailKey)) },
            )
    }
}
