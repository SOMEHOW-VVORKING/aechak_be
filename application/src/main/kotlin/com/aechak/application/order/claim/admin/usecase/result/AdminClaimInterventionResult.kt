package com.aechak.application.order.claim.admin.usecase.result

import com.aechak.application.order.claim.admin.port.view.AdminClaimInterventionView
import com.aechak.domain.order.claim.ClaimIntervention
import com.aechak.domain.order.claim.enums.ClaimFaultDecision
import com.aechak.domain.order.claim.enums.ClaimInterventionReason
import com.aechak.domain.order.claim.enums.ClaimStatus
import com.aechak.domain.order.claim.enums.ReturnCostBearer
import com.aechak.domain.order.claim.enums.SellerApprovalStatus
import java.time.LocalDateTime

/** 클레임에 남은 개입 기록. 사유 문구(label)는 서버가 소유하므로 코드와 함께 내린다. */
data class AdminClaimInterventionResult(
    val adminId: Long,
    val faultDecision: ClaimFaultDecision,
    val claimAccepted: Boolean,
    val reason: ClaimInterventionReason,
    val reasonLabel: String,
    val reasonDetail: String?,
    val returnCostBearer: ReturnCostBearer?,
    val previousStatus: ClaimStatus,
    val previousSellerApprovalStatus: SellerApprovalStatus?,
    val previousResolvedAt: LocalDateTime?,
    val internalMemo: String?,
    val intervenedAt: LocalDateTime,
) {
    companion object {
        fun from(view: AdminClaimInterventionView): AdminClaimInterventionResult =
            AdminClaimInterventionResult(
                adminId = view.adminId,
                faultDecision = view.faultDecision,
                claimAccepted = view.faultDecision.claimAccepted,
                reason = view.reason,
                reasonLabel = view.reason.label,
                reasonDetail = view.reasonDetail,
                returnCostBearer = view.returnCostBearer,
                previousStatus = view.previousStatus,
                previousSellerApprovalStatus = view.previousSellerApprovalStatus,
                previousResolvedAt = view.previousResolvedAt,
                internalMemo = view.internalMemo,
                intervenedAt = view.intervenedAt,
            )

        fun from(intervention: ClaimIntervention): AdminClaimInterventionResult =
            AdminClaimInterventionResult(
                adminId = intervention.adminId,
                faultDecision = intervention.faultDecision,
                claimAccepted = intervention.faultDecision.claimAccepted,
                reason = intervention.reason,
                reasonLabel = intervention.reason.label,
                reasonDetail = intervention.reasonDetail,
                returnCostBearer = intervention.returnCostBearer,
                previousStatus = intervention.previousStatus,
                previousSellerApprovalStatus = intervention.previousSellerApprovalStatus,
                previousResolvedAt = intervention.previousResolvedAt,
                internalMemo = intervention.internalMemo,
                intervenedAt = intervention.createdAt,
            )
    }
}
