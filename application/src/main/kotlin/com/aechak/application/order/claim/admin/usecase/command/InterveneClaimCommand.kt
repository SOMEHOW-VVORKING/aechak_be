package com.aechak.application.order.claim.admin.usecase.command

import com.aechak.domain.order.claim.enums.ClaimFaultDecision
import com.aechak.domain.order.claim.enums.ClaimInterventionReason
import com.aechak.domain.order.claim.enums.ReturnCostBearer

/**
 * 운영자 개입 입력.
 *
 * [returnCostBearer]를 비우면 판정의 기본값([ClaimFaultDecision.defaultReturnCostBearer])이 들어간다.
 * 취소 클레임은 반품 발송이 없어 어느 값을 보내도 비워진다.
 */
data class InterveneClaimCommand(
    val adminId: Long,
    val claimPublicId: String,
    val faultDecision: ClaimFaultDecision,
    val reason: ClaimInterventionReason,
    val reasonDetail: String?,
    val returnCostBearer: ReturnCostBearer?,
    val internalMemo: String?,
)
