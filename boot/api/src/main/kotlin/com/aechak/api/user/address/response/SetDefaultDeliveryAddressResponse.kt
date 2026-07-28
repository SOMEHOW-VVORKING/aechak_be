package com.aechak.api.user.address.response

import com.aechak.application.user.address.usecase.result.SetDefaultDeliveryAddressResult
import com.fasterxml.jackson.annotation.JsonProperty

data class SetDefaultDeliveryAddressResponse(
    val addressId: Long,
    @get:JsonProperty("isDefault")
    val isDefault: Boolean,
) {
    companion object {
        fun from(result: SetDefaultDeliveryAddressResult): SetDefaultDeliveryAddressResponse =
            SetDefaultDeliveryAddressResponse(result.addressId, result.isDefault)
    }
}
