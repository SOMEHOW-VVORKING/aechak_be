package com.aechak.application.order.claim.usecase.result

import com.aechak.application.order.claim.port.view.SellerClaimItemView
import com.aechak.application.order.claim.port.view.SellerClaimView
import com.aechak.domain.order.claim.enums.ClaimStatus
import com.aechak.domain.order.claim.enums.RefundStatus
import com.aechak.domain.order.order.Order
import com.aechak.domain.order.order.enums.OrderItemStatus
import com.aechak.domain.order.order.enums.OrderStatus
import java.time.LocalDateTime

/** 셀러 클레임 상세 — 목록 행 + 주문·수령자 정보 + 대상 품목 + 진행 이력. */
data class SellerClaimDetailResult(
    val claim: SellerClaimSummaryResult,
    val order: OrderInfo,
    val items: List<ItemInfo>,
    val history: List<HistoryEntry>,
) {
    /** 수령인명·연락처는 복호 평문이다 — 마스킹은 화면이 한다(심사 API 선례와 통일). */
    data class OrderInfo(
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

    data class ItemInfo(
        val orderItemId: Long,
        val productName: String,
        val optionName: String,
        val quantity: Int,
        val unitPrice: Long,
        val refundAmount: Long,
        val itemStatus: OrderItemStatus,
    )

    data class HistoryEntry(
        val event: SellerClaimHistoryEvent,
        val occurredAt: LocalDateTime,
    )

    companion object {
        fun of(
            view: SellerClaimView,
            summary: SellerClaimSummaryResult,
            order: Order,
            recipientName: String,
            contactNumber: String,
            items: List<SellerClaimItemView>,
        ): SellerClaimDetailResult {
            val address = order.orderGroup.deliveryAddress
            return SellerClaimDetailResult(
                claim = summary,
                order =
                    OrderInfo(
                        orderPublicId = order.publicId,
                        orderStatus = order.status,
                        orderedAt = order.createdAt,
                        sellerShippingFee = order.sellerShippingFee,
                        recipientName = recipientName,
                        contactNumber = contactNumber,
                        zipCode = address.zipCode,
                        baseAddress = address.baseAddress,
                        detailAddress = address.detailAddress,
                        deliveryMemo = address.deliveryMemo,
                    ),
                items =
                    items.map {
                        ItemInfo(
                            orderItemId = it.orderItemId,
                            productName = it.productName,
                            optionName = it.optionName,
                            quantity = it.quantity,
                            unitPrice = it.unitPrice,
                            refundAmount = it.refundAmount,
                            itemStatus = it.itemStatus,
                        )
                    },
                history = historyOf(view),
            )
        }

        /**
         * 이력 전용 테이블이 없어 클레임이 들고 있는 시각 컬럼에서 조립한다.
         * 종결 시각(resolvedAt)은 한 칸뿐이라 종결의 성격을 네 축으로 되짚어 이름을 붙인다.
         */
        private fun historyOf(view: SellerClaimView): List<HistoryEntry> =
            listOfNotNull(
                view.requestedAt?.let { HistoryEntry(SellerClaimHistoryEvent.REQUESTED, it) },
                view.collectionStartedAt?.let { HistoryEntry(SellerClaimHistoryEvent.COLLECTION_STARTED, it) },
                view.approvedAt?.let { HistoryEntry(SellerClaimHistoryEvent.APPROVED, it) },
                view.clawbackDepositedAt?.let { HistoryEntry(SellerClaimHistoryEvent.CLAWBACK_DEPOSITED, it) },
                view.resolvedAt?.let { HistoryEntry(resolvedEventOf(view), it) },
            ).sortedWith(compareBy({ it.occurredAt }, { it.event.ordinal }))

        private fun resolvedEventOf(view: SellerClaimView): SellerClaimHistoryEvent =
            when {
                view.refundStatus == RefundStatus.DONE -> SellerClaimHistoryEvent.REFUNDED
                view.status == ClaimStatus.REJECTED -> SellerClaimHistoryEvent.REJECTED
                view.status == ClaimStatus.WITHDRAWN -> SellerClaimHistoryEvent.WITHDRAWN
                view.status == ClaimStatus.ADMIN_OVERRIDDEN -> SellerClaimHistoryEvent.ADMIN_OVERRIDDEN
                else -> SellerClaimHistoryEvent.RESOLVED
            }
    }
}
