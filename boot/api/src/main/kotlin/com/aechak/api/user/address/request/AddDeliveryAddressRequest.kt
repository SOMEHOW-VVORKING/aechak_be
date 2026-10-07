package com.aechak.api.user.address.request

import com.aechak.application.user.address.usecase.command.AddDeliveryAddressCommand
import com.aechak.domain.user.address.DeliveryAddress
import com.aechak.webcommon.validation.NotBlankUnicode
import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class AddDeliveryAddressRequest(
    @field:NotBlankUnicode(message = "수령인 이름은 필수입니다.")
    @field:Size(max = DeliveryAddress.RECEIVER_NAME_MAX_LENGTH, message = "수령인 이름은 {max}자를 넘을 수 없습니다.")
    val receiverName: String,
    @field:NotBlank(message = "연락처는 필수입니다.")
    @field:Pattern(
        regexp = DeliveryAddressPatterns.CONTACT_NUMBER,
        message = "연락처 형식이 올바르지 않습니다.",
    )
    var contactNumber: String,
    @field:NotBlank(message = "우편번호는 필수입니다.")
    @field:Size(max = 10, message = "우편번호는 {max}자를 넘을 수 없습니다.")
    @field:Pattern(regexp = DeliveryAddressPatterns.ZIP_CODE, message = "우편번호는 5자리 숫자여야 합니다.")
    val zipCode: String,
    @field:NotBlankUnicode(message = "기본 주소는 필수입니다.")
    @field:Size(max = 512, message = "기본 주소는 {max}자를 넘을 수 없습니다.")
    val baseAddress: String,
    @field:NotBlankUnicode(message = "상세 주소는 필수입니다.")
    @field:Size(max = 512, message = "상세 주소는 {max}자를 넘을 수 없습니다.")
    val detailAddress: String,
    @field:Size(max = 255, message = "배송 메모는 {max}자를 넘을 수 없습니다.")
    val deliveryMemo: String? = null,
    @field:Size(max = 100, message = "배송지 별칭은 {max}자를 넘을 수 없습니다.")
    val label: String? = null,
    // is- 필드는 스키마에 'default'로 새서 이름 고정.
    // Boolean?인 이유: 안 보내면 Jackson 500. 생략=false.
    @get:JsonProperty("isDefault")
    val isDefault: Boolean? = null,
) {
    init {
        contactNumber = DeliveryAddressPatterns.normalizeContactNumber(contactNumber)
    }

    fun toCommand(userId: Long): AddDeliveryAddressCommand =
        AddDeliveryAddressCommand(
            userId = userId,
            receiverName = receiverName,
            contactNumber = contactNumber,
            zipCode = zipCode,
            baseAddress = baseAddress,
            detailAddress = detailAddress,
            deliveryMemo = deliveryMemo,
            label = label,
            isDefault = isDefault ?: false,
        )
}
