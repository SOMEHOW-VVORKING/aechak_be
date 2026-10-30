package com.aechak.application.order.claim.admin.port.view

import com.aechak.domain.order.claim.enums.ClaimFaultDecision
import com.aechak.domain.order.claim.enums.ClaimInterventionReason
import com.aechak.domain.order.claim.enums.ClaimStatus
import com.aechak.domain.order.claim.enums.ReturnCostBearer
import com.aechak.domain.order.claim.enums.SellerApprovalStatus
import java.time.LocalDateTime

/** 클레임에 남은 개입 기록 한 행. 한 클레임에 최대 하나다(claim_id UNIQUE). */
data class AdminClaimInterventionView(
    val claimId: Long,
    val adminId: Long,
    val faultDecision: ClaimFaultDecision,
    val reason: ClaimInterventionReason,
    val reasonDetail: String?,
    val returnCostBearer: ReturnCostBearer?,
    val previousStatus: ClaimStatus,
    val previousSellerApprovalStatus: SellerApprovalStatus?,
    val previousResolvedAt: LocalDateTime?,
    val internalMemo: String?,
    val intervenedAt: LocalDateTime,
)
