package com.aechak.application.user.address.usecase.result

import com.aechak.domain.user.address.DeliveryAddress

data class SetDefaultDeliveryAddressResult(
    val addressId: Long,
    val isDefault: Boolean,
) {
    companion object {
        fun from(address: DeliveryAddress): SetDefaultDeliveryAddressResult = SetDefaultDeliveryAddressResult(address.id, address.isDefault)
    }
}
