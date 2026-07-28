package com.aechak.api.user.address.response

import com.aechak.application.user.address.usecase.result.AddDeliveryAddressResult
import com.fasterxml.jackson.annotation.JsonProperty

data class AddDeliveryAddressResponse(
    val addressId: Long,
    // is-로 시작하는 필드는 JSON에서 'default'로 새서 이름을 못박는다.
    @get:JsonProperty("isDefault")
    val isDefault: Boolean,
    val totalCount: Int,
) {
    companion object {
        fun from(result: AddDeliveryAddressResult): AddDeliveryAddressResponse =
            AddDeliveryAddressResponse(result.addressId, result.isDefault, result.totalCount)
    }
}
