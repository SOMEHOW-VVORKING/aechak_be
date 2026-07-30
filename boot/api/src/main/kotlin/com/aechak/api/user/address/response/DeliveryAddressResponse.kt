package com.aechak.api.user.address.response

import com.aechak.application.user.address.usecase.result.DeliveryAddressResult
import com.fasterxml.jackson.annotation.JsonProperty

data class DeliveryAddressResponse(
    val addressId: Long,
    val receiverName: String,
    val contactNumber: String,
    val zipCode: String,
    val baseAddress: String,
    val detailAddress: String?,
    val deliveryMemo: String?,
    val label: String?,
    // is-로 시작하는 필드는 JSON에서 'default'로 새서 이름을 못박는다.
    @get:JsonProperty("isDefault")
    val isDefault: Boolean,
) {
    companion object {
        fun from(result: DeliveryAddressResult): DeliveryAddressResponse =
            DeliveryAddressResponse(
                addressId = result.addressId,
                receiverName = result.receiverName,
                contactNumber = result.contactNumber,
                zipCode = result.zipCode,
                baseAddress = result.baseAddress,
                detailAddress = result.detailAddress,
                deliveryMemo = result.deliveryMemo,
                label = result.label,
                isDefault = result.isDefault,
            )
    }
}
