package com.aechak.application.user.address.usecase.command

data class UpdateDeliveryAddressCommand(
    val userId: Long,
    val addressId: Long,
    val receiverName: String,
    val contactNumber: String,
    val zipCode: String,
    val baseAddress: String,
    val detailedAddress: String?,
    val deliveryMemo: String?,
    val label: String?,
    val isDefault: Boolean,
)
