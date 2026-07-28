package com.aechak.application.user.address.usecase.result

data class DeliveryAddressListResult(
    val addresses: List<DeliveryAddressResult>,
    val totalCount: Int,
)
