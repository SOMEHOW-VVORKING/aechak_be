package com.aechak.application.user.address.usecase.result

import com.aechak.domain.user.address.DeliveryAddress

data class UpdateDeliveryAddressResult(
    val addressId: Long,
    val isDefault: Boolean,
) {
    companion object {
        fun from(address: DeliveryAddress): UpdateDeliveryAddressResult = UpdateDeliveryAddressResult(address.id, address.isDefault)
    }
}
