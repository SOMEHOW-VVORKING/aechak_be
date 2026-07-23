package com.aechak.application.user.address.usecase.command

import com.aechak.domain.user.address.DeliveryAddress
import com.aechak.domain.user.user.User

data class AddDeliveryAddressCommand(
    val userId: Long,
    val receiverName: String,
    val contactNumber: String,
    val zipCode: String,
    val baseAddress: String,
    val detailedAddress: String?,
    val deliveryMemo: String?,
    val label: String?,
    val isDefault: Boolean,
) {
    /** 필드 옮겨 담기만. 생성 규칙은 register 소유, user·인코딩 연락처는 서비스가 채움. */
    fun toEntity(
        user: User,
        encodedContact: ByteArray,
    ): DeliveryAddress =
        DeliveryAddress.register(
            user = user,
            receiverName = receiverName,
            contactNumber = encodedContact,
            zipCode = zipCode,
            baseAddress = baseAddress,
            detailAddress = detailedAddress,
            deliveryMemo = deliveryMemo,
            label = label,
            isDefault = isDefault,
        )
}
