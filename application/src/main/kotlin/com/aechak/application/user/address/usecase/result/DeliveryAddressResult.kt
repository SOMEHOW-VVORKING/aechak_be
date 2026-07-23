package com.aechak.application.user.address.usecase.result

import com.aechak.domain.user.address.DeliveryAddress

/** contactNumber는 이미 평문. */
data class DeliveryAddressResult(
    val addressId: Long,
    val receiverName: String,
    val contactNumber: String,
    val zipCode: String,
    val baseAddress: String,
    val detailedAddress: String?,
    val deliveryMemo: String?,
    val label: String?,
    val isDefault: Boolean,
) {
    companion object {
        /** contactNumber는 복호된 평문을 받음 — 코덱은 서비스 소유. */
        fun from(
            address: DeliveryAddress,
            contactNumber: String,
        ): DeliveryAddressResult =
            DeliveryAddressResult(
                addressId = address.id,
                receiverName = address.receiverName,
                contactNumber = contactNumber,
                zipCode = address.zipCode,
                baseAddress = address.baseAddress,
                detailedAddress = address.detailAddress,
                deliveryMemo = address.deliveryMemo,
                label = address.label,
                isDefault = address.isDefault,
            )
    }
}
