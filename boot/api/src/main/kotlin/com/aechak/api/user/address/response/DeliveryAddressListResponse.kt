package com.aechak.api.user.address.response

import com.aechak.application.user.address.usecase.result.DeliveryAddressListResult

data class DeliveryAddressListResponse(
    val addresses: List<DeliveryAddressResponse>,
    val totalCount: Int,
) {
    companion object {
        fun from(result: DeliveryAddressListResult): DeliveryAddressListResponse =
            DeliveryAddressListResponse(
                addresses = result.addresses.map(DeliveryAddressResponse::from),
                totalCount = result.totalCount,
            )
    }
}
