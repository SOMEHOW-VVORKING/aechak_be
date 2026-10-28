package com.aechak.application.order.claim.port.view

import com.aechak.domain.order.claim.enums.ClaimReasonCode
import com.aechak.domain.order.claim.enums.ClaimSettlementStatus
import com.aechak.domain.order.claim.enums.ClaimStatus
import com.aechak.domain.order.claim.enums.ClaimType
import com.aechak.domain.order.claim.enums.RefundStatus
import com.aechak.domain.order.claim.enums.ReturnCostBearer
import com.aechak.domain.order.claim.enums.SellerApprovalStatus
import java.time.LocalDateTime

/**
 * 셀러 클레임 한 행의 읽기 모델. 상태는 서버의 네 축(클레임 상태·셀러 승인·환불·정산)을 그대로 담는다 —
 * 화면 한 값으로 접는 일은 프론트가 한다.
 */
data class SellerClaimView(
    val claimId: Long,
    val claimPublicId: String,
    val orderId: Long,
    val orderGroupId: Long,
    val orderPublicId: String,
    val claimType: ClaimType,
    val reasonCode: ClaimReasonCode?,
    val reasonDetail: String?,
    val status: ClaimStatus,
    val refundStatus: RefundStatus?,
    val sellerApprovalStatus: SellerApprovalStatus?,
    val settlementStatus: ClaimSettlementStatus?,
    val returnCostBearer: ReturnCostBearer?,
    val rejectionReason: String?,
    val sellerMemo: String?,
    val requestedAt: LocalDateTime?,
    val autoApproveDueAt: LocalDateTime?,
    val approvedAt: LocalDateTime?,
    val collectionStartedAt: LocalDateTime?,
    val clawbackDepositedAt: LocalDateTime?,
    val resolvedAt: LocalDateTime?,
)
