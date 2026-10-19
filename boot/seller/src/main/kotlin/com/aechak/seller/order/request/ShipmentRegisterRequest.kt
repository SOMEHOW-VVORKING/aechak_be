package com.aechak.seller.order.request

import com.aechak.application.order.seller.usecase.command.RegisterShipmentCommand
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class ShipmentRegisterRequest(
    @field:NotBlank(message = "택배사 코드는 필수입니다.")
    @field:Size(max = 30, message = "택배사 코드는 {max}자를 넘을 수 없습니다.")
    val courierCode: String,
    @field:NotBlank(message = "송장번호는 필수입니다.")
    @field:Size(max = 50, message = "송장번호는 {max}자를 넘을 수 없습니다.")
    val trackingNumber: String,
) {
    fun toCommand(
        sellerId: Long,
        orderPublicId: String,
    ): RegisterShipmentCommand =
        RegisterShipmentCommand(
            sellerId = sellerId,
            orderPublicId = orderPublicId,
            courierCode = courierCode.trim(),
            trackingNumber = trackingNumber.trim(),
        )
}
