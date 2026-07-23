package com.aechak.api.user.address.request

import com.aechak.application.user.address.usecase.command.UpdateDeliveryAddressCommand
import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

/**
 * PATCH지만 바디는 전체 전송. 부분 수정 전환 시 이 DTO만 전 필드 nullable로 변경하면 됨.
 */
data class UpdateDeliveryAddressRequest(
    @field:NotBlank(message = "수령인 이름은 필수입니다.")
    @field:Size(max = 255, message = "수령인 이름은 {max}자를 넘을 수 없습니다.")
    val receiverName: String,
    @field:NotBlank(message = "연락처는 필수입니다.")
    @field:Pattern(
        regexp = DeliveryAddressPatterns.CONTACT_NUMBER,
        message = "연락처 형식이 올바르지 않습니다.",
    )
    val contactNumber: String,
    @field:NotBlank(message = "우편번호는 필수입니다.")
    @field:Size(max = 10, message = "우편번호는 {max}자를 넘을 수 없습니다.")
    @field:Pattern(regexp = DeliveryAddressPatterns.ZIP_CODE, message = "우편번호는 5자리 숫자여야 합니다.")
    val zipCode: String,
    @field:NotBlank(message = "기본 주소는 필수입니다.")
    @field:Size(max = 512, message = "기본 주소는 {max}자를 넘을 수 없습니다.")
    val baseAddress: String,
    @field:Size(max = 512, message = "상세 주소는 {max}자를 넘을 수 없습니다.")
    val detailedAddress: String? = null,
    @field:Size(max = 255, message = "배송 메모는 {max}자를 넘을 수 없습니다.")
    val deliveryMemo: String? = null,
    @field:Size(max = 100, message = "배송지 별칭은 {max}자를 넘을 수 없습니다.")
    val label: String? = null,
    // true=기본 지정. 생략/false=유지(해제는 setDefault·삭제 승격 몫). 등록(생략=비기본)과 뜻 다름.
    @get:JsonProperty("isDefault")
    val isDefault: Boolean? = null,
) {
    fun toCommand(
        userId: Long,
        addressId: Long,
    ): UpdateDeliveryAddressCommand =
        UpdateDeliveryAddressCommand(
            userId = userId,
            addressId = addressId,
            receiverName = receiverName,
            contactNumber = contactNumber,
            zipCode = zipCode,
            baseAddress = baseAddress,
            detailedAddress = detailedAddress,
            deliveryMemo = deliveryMemo,
            label = label,
            isDefault = isDefault ?: false,
        )
}
