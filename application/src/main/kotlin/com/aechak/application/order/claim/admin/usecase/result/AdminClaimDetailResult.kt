package com.aechak.application.order.claim.admin.usecase.result

import com.aechak.application.order.claim.admin.port.view.AdminClaimView
import com.aechak.application.order.claim.port.view.SellerClaimItemView
import com.aechak.application.order.claim.usecase.result.SellerClaimHistoryEvent
import com.aechak.domain.order.claim.enums.ClaimReasonCode
import com.aechak.domain.order.claim.enums.ClaimStatus
import com.aechak.domain.order.claim.enums.RefundStatus
import com.aechak.domain.order.claim.enums.ReturnCostBearer
import com.aechak.domain.order.claim.enums.SellerApprovalStatus
import com.aechak.domain.order.order.Order
import com.aechak.domain.order.order.enums.OrderItemStatus
import com.aechak.domain.order.order.enums.OrderStatus
import java.time.LocalDateTime

/**
 * 어드민 클레임 상세. 구매자 주장([buyerStatement])과 셀러 처리([sellerHandling])를 **따로 떼어** 내린다 —
 * 어느 한쪽만 보면 귀책을 판정할 수 없으므로 두 블록이 항상 함께 실려야 하고,
 * 블록을 나누면 화면이 둘을 나란히 놓을 때 어느 값이 누구 것인지 헷갈릴 자리가 없다.
 */
data class AdminClaimDetailResult(
    val claim: AdminClaimSummaryResult,
    val order: OrderInfo,
    val buyerStatement: BuyerStatement,
    val sellerHandling: SellerHandling,
    val items: List<ItemInfo>,
    val history: List<HistoryEntry>,
    val intervention: AdminClaimInterventionResult?,
) {
    /** 수령인명·연락처는 복호 평문이다 — 마스킹은 화면이 한다(셀러·심사 API 선례와 통일). */
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

    /** 구매자가 낸 주장. 사유 문구(label)는 서버가 소유하므로 코드와 함께 내린다. */
    data class BuyerStatement(
        val reasonCode: ClaimReasonCode?,
        val reasonLabel: String?,
        val reasonDetail: String?,
        val requestedAt: LocalDateTime?,
    )

    /**
     * 셀러가 한 처리. [sellerMemo]는 셀러 운영 메모로 구매자에게는 나가지 않는 값이지만,
     * 분쟁 판정에서는 셀러측 근거라 운영자에게는 보인다.
     */
    data class SellerHandling(
        val sellerApprovalStatus: SellerApprovalStatus?,
        val rejectionReason: String?,
        val sellerMemo: String?,
        val returnCostBearer: ReturnCostBearer?,
        val autoApproveDueAt: LocalDateTime?,
        val approvedAt: LocalDateTime?,
        val collectionStartedAt: LocalDateTime?,
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
            view: AdminClaimView,
            summary: AdminClaimSummaryResult,
            order: Order,
            recipientName: String,
            contactNumber: String,
            items: List<SellerClaimItemView>,
            intervention: AdminClaimInterventionResult?,
        ): AdminClaimDetailResult {
            val address = order.orderGroup.deliveryAddress
            return AdminClaimDetailResult(
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
                buyerStatement =
                    BuyerStatement(
                        reasonCode = view.reasonCode,
                        reasonLabel = view.reasonCode?.label,
                        reasonDetail = view.reasonDetail,
                        requestedAt = view.requestedAt,
                    ),
                sellerHandling =
                    SellerHandling(
                        sellerApprovalStatus = view.sellerApprovalStatus,
                        rejectionReason = view.rejectionReason,
                        sellerMemo = view.sellerMemo,
                        returnCostBearer = view.returnCostBearer,
                        autoApproveDueAt = view.autoApproveDueAt,
                        approvedAt = view.approvedAt,
                        collectionStartedAt = view.collectionStartedAt,
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
                history = historyOf(view, intervention),
                intervention = intervention,
            )
        }

        /**
         * 이력 전용 테이블이 없어 클레임이 들고 있는 시각 칼럼에서 조립한다.
         *
         * 개입이 claims.resolved_at을 자기 시각으로 덮으므로, 뒤집힌 건의 셀러 종결 시각은
         * 개입 기록의 previousResolvedAt에서 되살린다 — 운영자가 보는 이력에서 "셀러가 거부했고
         * 운영자가 뒤집었다"는 두 사건이 모두 보여야 판정 근거가 된다.
         */
        private fun historyOf(
            view: AdminClaimView,
            intervention: AdminClaimInterventionResult?,
        ): List<HistoryEntry> {
            val base =
                listOfNotNull(
                    view.requestedAt?.let { HistoryEntry(SellerClaimHistoryEvent.REQUESTED, it) },
                    view.collectionStartedAt?.let { HistoryEntry(SellerClaimHistoryEvent.COLLECTION_STARTED, it) },
                    view.approvedAt?.let { HistoryEntry(SellerClaimHistoryEvent.APPROVED, it) },
                    view.clawbackDepositedAt?.let { HistoryEntry(SellerClaimHistoryEvent.CLAWBACK_DEPOSITED, it) },
                )
            val closings =
                if (intervention == null) {
                    listOfNotNull(view.resolvedAt?.let { HistoryEntry(resolvedEventOf(view), it) })
                } else {
                    listOfNotNull(
                        intervention.previousResolvedAt?.let {
                            HistoryEntry(resolvedEventOf(intervention.previousStatus, view.refundStatus), it)
                        },
                        HistoryEntry(SellerClaimHistoryEvent.ADMIN_OVERRIDDEN, intervention.intervenedAt),
                    )
                }
            return (base + closings).sortedWith(compareBy({ it.occurredAt }, { it.event.ordinal }))
        }

        private fun resolvedEventOf(view: AdminClaimView): SellerClaimHistoryEvent = resolvedEventOf(view.status, view.refundStatus)

        private fun resolvedEventOf(
            status: ClaimStatus,
            refundStatus: RefundStatus?,
        ): SellerClaimHistoryEvent =
            when {
                refundStatus == RefundStatus.DONE -> SellerClaimHistoryEvent.REFUNDED
                status == ClaimStatus.REJECTED -> SellerClaimHistoryEvent.REJECTED
                status == ClaimStatus.WITHDRAWN -> SellerClaimHistoryEvent.WITHDRAWN
                status == ClaimStatus.ADMIN_OVERRIDDEN -> SellerClaimHistoryEvent.ADMIN_OVERRIDDEN
                else -> SellerClaimHistoryEvent.RESOLVED
            }
    }
}
