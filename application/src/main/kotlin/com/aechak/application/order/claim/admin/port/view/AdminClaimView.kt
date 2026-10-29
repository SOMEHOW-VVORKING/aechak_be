package com.aechak.application.order.claim.admin.port.view

import com.aechak.domain.order.claim.enums.ClaimReasonCode
import com.aechak.domain.order.claim.enums.ClaimSettlementStatus
import com.aechak.domain.order.claim.enums.ClaimStatus
import com.aechak.domain.order.claim.enums.ClaimType
import com.aechak.domain.order.claim.enums.RefundStatus
import com.aechak.domain.order.claim.enums.ReturnCostBearer
import com.aechak.domain.order.claim.enums.SellerApprovalStatus
import java.time.LocalDateTime

/**
 * 어드민 클레임 한 행의 읽기 모델. 상태는 서버의 네 축(클레임 상태·셀러 승인·환불·정산)을 그대로 담고
 * 한 값으로 접는 일은 화면이 한다 — 셀러 읽기 모델과 같은 규칙이다.
 *
 * 셀러·구매자 식별 정보가 함께 실린다: 운영자는 양쪽을 다 보고 판정하므로 둘 중 하나만으로는
 * 목록 한 행이 쓸모가 없다. 셀러명은 주문 시점 스냅샷(orders.seller_name_snapshot)이고
 * 구매자는 닉네임 — 실명·연락처는 목록에 싣지 않는다.
 */
data class AdminClaimView(
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
    val adminReason: String?,
    val requestedAt: LocalDateTime?,
    val autoApproveDueAt: LocalDateTime?,
    val approvedAt: LocalDateTime?,
    val collectionStartedAt: LocalDateTime?,
    val clawbackDepositedAt: LocalDateTime?,
    val resolvedAt: LocalDateTime?,
    val sellerId: Long,
    val sellerName: String?,
    val buyerId: Long,
    val buyerPublicId: String?,
    val buyerNickname: String?,
)
