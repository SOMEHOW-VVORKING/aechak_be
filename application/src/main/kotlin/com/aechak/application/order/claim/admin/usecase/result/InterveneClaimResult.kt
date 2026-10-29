package com.aechak.application.order.claim.admin.usecase.result

import com.aechak.domain.order.claim.Claim
import com.aechak.domain.order.claim.ClaimIntervention
import com.aechak.domain.order.claim.enums.ClaimStatus
import com.aechak.domain.order.claim.enums.RefundStatus
import com.aechak.domain.order.claim.enums.ReturnCostBearer

/**
 * 개입 결과.
 *
 * [refundStatus]와 [refundSettled]를 함께 내리는 이유: 개입은 반품비 부담 주체만 정하고
 * 환불 집행·결제 취소는 건드리지 않는다. 판정 버튼을 누른 운영자가 "환불까지 처리됐다"고 읽으면
 * 돌려주지 않은 돈을 처리된 것으로 보게 되므로, 환불 축을 응답에 그대로 실어 그렇지 않음을 못박는다.
 */
data class InterveneClaimResult(
    val claimPublicId: String,
    val status: ClaimStatus,
    val returnCostBearer: ReturnCostBearer?,
    val refundStatus: RefundStatus?,
    val refundSettled: Boolean,
    val intervention: AdminClaimInterventionResult,
) {
    companion object {
        fun of(
            claim: Claim,
            intervention: ClaimIntervention,
        ): InterveneClaimResult =
            InterveneClaimResult(
                claimPublicId = claim.publicId,
                status = claim.status,
                returnCostBearer = claim.returnCostBearer,
                refundStatus = claim.refundStatus,
                refundSettled = claim.refundStatus == RefundStatus.DONE,
                intervention = AdminClaimInterventionResult.from(intervention),
            )
    }
}
