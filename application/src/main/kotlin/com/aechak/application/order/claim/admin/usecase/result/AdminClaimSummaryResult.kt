package com.aechak.application.order.claim.admin.usecase.result

import com.aechak.application.order.claim.admin.port.view.AdminClaimView
import com.aechak.application.order.claim.port.view.SellerClaimItemView
import com.aechak.domain.order.claim.enums.ClaimFaultDecision
import com.aechak.domain.order.claim.enums.ClaimReasonCode
import com.aechak.domain.order.claim.enums.ClaimSettlementStatus
import com.aechak.domain.order.claim.enums.ClaimStatus
import com.aechak.domain.order.claim.enums.ClaimType
import com.aechak.domain.order.claim.enums.RefundStatus
import com.aechak.domain.order.claim.enums.ReturnCostBearer
import com.aechak.domain.order.claim.enums.SellerApprovalStatus
import java.time.Duration
import java.time.LocalDateTime

/**
 * 어드민 클레임 목록 한 행. 네 축(클레임 상태·셀러 승인·환불·정산)을 그대로 내리고 한 값으로 접지 않는다.
 *
 * [disputeElapsedDays]는 목록 기본 정렬(오래된 순)이 왜 그 순서인지를 화면에서 읽을 수 있게 하는 값이다 —
 * 종결된 건은 종결까지 걸린 일수, 진행 중인 건은 지금까지 걸린 일수다.
 * [interventionAllowed]는 운영자가 지금 개입할 수 있는 건인지로, 목록에서 바로 작업 대상을 가른다.
 */
data class AdminClaimSummaryResult(
    val claimPublicId: String,
    val orderPublicId: String,
    val claimType: ClaimType,
    val reasonCode: ClaimReasonCode?,
    val reasonDetail: String?,
    val status: ClaimStatus,
    val refundStatus: RefundStatus?,
    val sellerApprovalStatus: SellerApprovalStatus?,
    val settlementStatus: ClaimSettlementStatus?,
    val returnCostBearer: ReturnCostBearer?,
    val refundAmount: Long,
    val returnShippingFee: Long?,
    val productNames: List<String>,
    val itemCount: Int,
    val sellerId: Long,
    val sellerName: String?,
    val buyerPublicId: String?,
    val buyerNickname: String?,
    val faultDecision: ClaimFaultDecision?,
    val interventionAllowed: Boolean,
    val requestedAt: LocalDateTime?,
    val autoApproveDueAt: LocalDateTime?,
    val approvedAt: LocalDateTime?,
    val resolvedAt: LocalDateTime?,
    val disputeElapsedDays: Long?,
) {
    companion object {
        fun of(
            view: AdminClaimView,
            items: List<SellerClaimItemView>,
            refundAmount: Long,
            returnShippingFee: Long?,
            intervention: AdminClaimInterventionResult?,
            interventionAllowed: Boolean,
            now: LocalDateTime,
        ): AdminClaimSummaryResult =
            AdminClaimSummaryResult(
                claimPublicId = view.claimPublicId,
                orderPublicId = view.orderPublicId,
                claimType = view.claimType,
                reasonCode = view.reasonCode,
                reasonDetail = view.reasonDetail,
                status = view.status,
                refundStatus = view.refundStatus,
                sellerApprovalStatus = view.sellerApprovalStatus,
                settlementStatus = view.settlementStatus,
                returnCostBearer = view.returnCostBearer,
                refundAmount = refundAmount,
                returnShippingFee = returnShippingFee,
                productNames = items.map { it.productName }.distinct(),
                itemCount = items.size,
                sellerId = view.sellerId,
                sellerName = view.sellerName,
                buyerPublicId = view.buyerPublicId,
                buyerNickname = view.buyerNickname,
                faultDecision = intervention?.faultDecision,
                interventionAllowed = interventionAllowed,
                requestedAt = view.requestedAt,
                autoApproveDueAt = view.autoApproveDueAt,
                approvedAt = view.approvedAt,
                resolvedAt = view.resolvedAt,
                disputeElapsedDays = elapsedDaysOf(view, now),
            )

        private fun elapsedDaysOf(
            view: AdminClaimView,
            now: LocalDateTime,
        ): Long? {
            val from = view.requestedAt ?: return null
            val to = view.resolvedAt ?: now
            // 요청보다 이른 종결 시각(시계 역행·수동 보정)이 음수 경과일로 나가지 않게 0에서 끊는다
            return Duration.between(from, to).toDays().coerceAtLeast(0L)
        }
    }
}
