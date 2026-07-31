package com.aechak.application.user.address.usecase.result

import com.aechak.domain.user.address.DeliveryAddress

data class AddDeliveryAddressResult(
    val addressId: Long,
    val isDefault: Boolean,
    val totalCount: Int,
) {
    companion object {
        fun from(
            address: DeliveryAddress,
            totalCount: Int,
        ): AddDeliveryAddressResult = AddDeliveryAddressResult(address.id, address.isDefault, totalCount)
    }
}
