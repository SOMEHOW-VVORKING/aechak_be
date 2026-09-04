package com.aechak.domain.order.group

import jakarta.persistence.Column
import jakarta.persistence.Embeddable

/**
 * 수령인명과 연락처는 AES 암호문(Base64)을 담음. 평문을 넣지 않음
 * 암복호는 application의 PiiCrypto 소관
 */
@Embeddable
class DeliveryAddressSnapshot(
    // 암호문 컬럼은 평문 상한(DeliveryAddress.RECEIVER_NAME_MAX_LENGTH)의 팽창 후 길이가 들어가야 함 — 정합은 PiiCryptoAdapterTest가 고정
    @Column(name = "receiver_name_enc", length = 255)
    val receiverNameEnc: String,
    @Column(name = "contact_number_enc", length = 255)
    val contactNumberEnc: String,
    @Column(name = "zip_code", length = 255)
    val zipCode: String,
    @Column(name = "base_address", length = 512)
    val baseAddress: String,
    @Column(name = "detail_address", length = 512)
    val detailAddress: String?,
    @Column(name = "delivery_memo", length = 255)
    val deliveryMemo: String?,
)
