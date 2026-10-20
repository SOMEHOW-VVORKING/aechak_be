package com.aechak.seller.order.request

import com.aechak.application.order.claim.usecase.command.UpdateClaimMemoCommand
import com.aechak.domain.order.claim.Claim
import jakarta.validation.constraints.Size

/** 운영 메모 덮어쓰기. null·공백을 보내면 메모를 지운다. */
data class UpdateClaimMemoRequest(
    @field:Size(max = Claim.SELLER_MEMO_MAX_LENGTH, message = "sellerMemo는 최대 {max}자입니다.")
    val sellerMemo: String? = null,
) {
    fun toCommand(
        sellerId: Long,
        claimPublicId: String,
    ): UpdateClaimMemoCommand =
        UpdateClaimMemoCommand(
            sellerId = sellerId,
            claimPublicId = claimPublicId,
            sellerMemo = sellerMemo,
        )
}
