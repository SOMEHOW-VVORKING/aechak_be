package com.aechak.api.order.claim.request

import com.aechak.application.order.claim.usecase.command.RequestClaimCommand
import com.aechak.domain.order.claim.Claim
import com.aechak.domain.order.claim.enums.ClaimReasonCode
import com.aechak.domain.order.claim.enums.ClaimType
import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Size

data class RequestClaimRequest(
    val claimType: ClaimType,
    /** 고정 어휘다 — 어휘에 없는 값은 본문 파싱에서 걸러 400으로 돌려준다. */
    val reasonCode: ClaimReasonCode,
    @field:Size(max = Claim.REASON_MAX_LENGTH, message = "상세 사유는 500자까지 입력할 수 있습니다.")
    val reasonDetail: String? = null,
    /** 생략하면 주문 전체를 대상으로 본다. */
    @field:Valid
    val items: List<ClaimItemRequest> = emptyList(),
) {
    data class ClaimItemRequest(
        val orderItemId: Long,
        @field:Min(value = 1, message = "취소 수량은 1개 이상이어야 합니다.")
        val quantity: Int,
    )

    fun toCommand(
        buyerId: Long,
        orderPublicId: String,
    ): RequestClaimCommand =
        RequestClaimCommand(
            buyerId = buyerId,
            orderPublicId = orderPublicId,
            claimType = claimType,
            reasonCode = reasonCode,
            reasonDetail = reasonDetail,
            targets = items.map { RequestClaimCommand.ClaimTarget(orderItemId = it.orderItemId, quantity = it.quantity) },
        )
}
