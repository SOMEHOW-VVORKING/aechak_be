package com.aechak.seller.order.response

import com.aechak.application.order.claim.usecase.result.SellerClaimDetailResult
import com.aechak.application.order.claim.usecase.result.SellerClaimHistoryEvent
import com.aechak.domain.order.order.enums.OrderItemStatus
import com.aechak.domain.order.order.enums.OrderStatus
import java.time.LocalDateTime

/** 수령인명·연락처는 복호 평문이다 — 마스킹은 화면이 한다. */
data class SellerClaimDetailResponse(
    val claim: SellerClaimSummaryResponse,
    val order: OrderResponse,
    val items: List<ItemResponse>,
    val history: List<HistoryResponse>,
) {
    data class OrderResponse(
        val orderPublicId: String,
        val orderStatus: OrderStatus,
        val orderedAt: LocalDateTime,
        val sellerShippingFee: Long,
        val recipientName: String,
        val contactNumber: String,
        val zipCode: String,
        val baseAddress: String,
        val detailAddress: String?,
        val deliveryMemo: String?,
    )

    data class ItemResponse(
        val orderItemId: Long,
        val productName: String,
        val optionName: String,
        val quantity: Int,
        val unitPrice: Long,
        val refundAmount: Long,
        val itemStatus: OrderItemStatus,
    )

    data class HistoryResponse(
        val event: SellerClaimHistoryEvent,
        val occurredAt: LocalDateTime,
    )

    companion object {
        fun from(result: SellerClaimDetailResult): SellerClaimDetailResponse =
            SellerClaimDetailResponse(
                claim = SellerClaimSummaryResponse.from(result.claim),
                order =
                    OrderResponse(
                        orderPublicId = result.order.orderPublicId,
                        orderStatus = result.order.orderStatus,
                        orderedAt = result.order.orderedAt,
                        sellerShippingFee = result.order.sellerShippingFee,
                        recipientName = result.order.recipientName,
                        contactNumber = result.order.contactNumber,
                        zipCode = result.order.zipCode,
                        baseAddress = result.order.baseAddress,
                        detailAddress = result.order.detailAddress,
                        deliveryMemo = result.order.deliveryMemo,
                    ),
                items =
                    result.items.map {
                        ItemResponse(
                            orderItemId = it.orderItemId,
                            productName = it.productName,
                            optionName = it.optionName,
                            quantity = it.quantity,
                            unitPrice = it.unitPrice,
                            refundAmount = it.refundAmount,
                            itemStatus = it.itemStatus,
                        )
                    },
                history = result.history.map { HistoryResponse(it.event, it.occurredAt) },
            )
    }
}
