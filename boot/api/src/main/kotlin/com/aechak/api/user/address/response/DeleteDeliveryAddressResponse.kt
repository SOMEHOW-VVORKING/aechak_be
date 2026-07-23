package com.aechak.api.user.address.response

import com.aechak.application.user.address.usecase.result.DeleteDeliveryAddressResult

data class DeleteDeliveryAddressResponse(
    val promotedDefaultAddressId: Long?,
) {
    companion object {
        fun from(result: DeleteDeliveryAddressResult): DeleteDeliveryAddressResponse =
            DeleteDeliveryAddressResponse(result.promotedDefaultAddressId)
    }
}
