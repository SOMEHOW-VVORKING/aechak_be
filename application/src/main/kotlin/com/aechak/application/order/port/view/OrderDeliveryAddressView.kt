package com.aechak.application.order.port.view

/** 주문 시점 배송지 스냅샷. 수령인명·연락처는 AES 암호문(Base64)이고 복호는 유스케이스가 한다. */
data class OrderDeliveryAddressView(
    val receiverNameEnc: String,
    val contactNumberEnc: String,
    val zipCode: String,
    val baseAddress: String,
    val detailAddress: String?,
    val deliveryMemo: String?,
)
