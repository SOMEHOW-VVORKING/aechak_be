package com.aechak.api.user.address.response

import com.aechak.application.user.address.usecase.result.UpdateDeliveryAddressResult
import com.fasterxml.jackson.annotation.JsonProperty

data class UpdateDeliveryAddressResponse(
    val addressId: Long,
    @get:JsonProperty("isDefault")
    val isDefault: Boolean,
) {
    companion object {
        fun from(result: UpdateDeliveryAddressResult): UpdateDeliveryAddressResponse =
            UpdateDeliveryAddressResponse(result.addressId, result.isDefault)
    }
}
