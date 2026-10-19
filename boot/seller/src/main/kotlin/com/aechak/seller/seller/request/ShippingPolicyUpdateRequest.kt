package com.aechak.seller.seller.request

import com.aechak.application.seller.usecase.command.UpdateShippingPolicyCommand
import jakarta.validation.constraints.Size

/**
 * 배송 정책 전체 덮어쓰기 요청. 금액·발송일의 범위 검증은 도메인이 한다 —
 * 음수·자기모순을 10005 하나로 모아 내리기 위해 여기서는 길이만 본다.
 */
data class ShippingPolicyUpdateRequest(
    @field:Size(max = 30, message = "택배사 코드는 30자 이하여야 합니다.")
    val defaultCourierCode: String? = null,
    val baseShippingFee: Long,
    val freeShippingThreshold: Long? = null,
    @field:Size(max = 10, message = "출고지 우편번호는 10자 이하여야 합니다.")
    val originZipCode: String? = null,
    @field:Size(max = 255, message = "출고지 도로명주소는 255자 이하여야 합니다.")
    val originRoadAddress: String? = null,
    @field:Size(max = 255, message = "출고지 상세주소는 255자 이하여야 합니다.")
    val originDetailAddress: String? = null,
    val returnSameAsOrigin: Boolean = true,
    @field:Size(max = 10, message = "반품지 우편번호는 10자 이하여야 합니다.")
    val returnZipCode: String? = null,
    @field:Size(max = 255, message = "반품지 도로명주소는 255자 이하여야 합니다.")
    val returnRoadAddress: String? = null,
    @field:Size(max = 255, message = "반품지 상세주소는 255자 이하여야 합니다.")
    val returnDetailAddress: String? = null,
    val jejuExtraFee: Long,
    val remoteAreaExtraFee: Long,
    val returnFee: Long,
    val exchangeFee: Long,
    val avgDispatchDays: Int,
    val shippingPolicyText: String? = null,
) {
    fun toCommand(sellerId: Long): UpdateShippingPolicyCommand =
        UpdateShippingPolicyCommand(
            sellerId = sellerId,
            defaultCourierCode = defaultCourierCode,
            baseShippingFee = baseShippingFee,
            freeShippingThreshold = freeShippingThreshold,
            originZipCode = originZipCode,
            originRoadAddress = originRoadAddress,
            originDetailAddress = originDetailAddress,
            returnSameAsOrigin = returnSameAsOrigin,
            returnZipCode = returnZipCode,
            returnRoadAddress = returnRoadAddress,
            returnDetailAddress = returnDetailAddress,
            jejuExtraFee = jejuExtraFee,
            remoteAreaExtraFee = remoteAreaExtraFee,
            returnFee = returnFee,
            exchangeFee = exchangeFee,
            avgDispatchDays = avgDispatchDays,
            shippingPolicyText = shippingPolicyText,
        )
}
