package com.aechak.seller.order.request

import com.aechak.application.order.claim.usecase.command.ReshipClaimCommand
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class ReshipClaimRequest(
    @field:NotBlank(message = "택배사를 선택해 주세요.")
    @field:Size(max = COURIER_CODE_MAX_LENGTH, message = "택배사 코드가 너무 깁니다.")
    val courierCode: String = "",
    @field:NotBlank(message = "송장번호를 입력해 주세요.")
    @field:Size(max = TRACKING_NUMBER_MAX_LENGTH, message = "송장번호가 너무 깁니다.")
    val trackingNumber: String = "",
) {
    fun toCommand(
        sellerId: Long,
        claimPublicId: String,
    ): ReshipClaimCommand =
        ReshipClaimCommand(
            sellerId = sellerId,
            claimPublicId = claimPublicId,
            courierCode = courierCode.trim(),
            trackingNumber = trackingNumber.trim(),
        )

    private companion object {
        const val COURIER_CODE_MAX_LENGTH = 30
        const val TRACKING_NUMBER_MAX_LENGTH = 50
    }
}
