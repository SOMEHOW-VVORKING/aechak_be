package com.aechak.seller.seller.response

import com.aechak.application.seller.usecase.result.ShippingPolicyResult

data class ShippingPolicyResponse(
    val defaultCourierCode: String?,
    val baseShippingFee: Long,
    val freeShippingThreshold: Long?,
    val originZipCode: String?,
    val originRoadAddress: String?,
    val originDetailAddress: String?,
    val returnSameAsOrigin: Boolean,
    val returnZipCode: String?,
    val returnRoadAddress: String?,
    val returnDetailAddress: String?,
    val jejuExtraFee: Long,
    val remoteAreaExtraFee: Long,
    val returnFee: Long,
    val exchangeFee: Long,
    val avgDispatchDays: Int,
    val shippingPolicyText: String?,
) {
    companion object {
        fun from(result: ShippingPolicyResult): ShippingPolicyResponse =
            ShippingPolicyResponse(
                defaultCourierCode = result.defaultCourierCode,
                baseShippingFee = result.baseShippingFee,
                freeShippingThreshold = result.freeShippingThreshold,
                originZipCode = result.originZipCode,
                originRoadAddress = result.originRoadAddress,
                originDetailAddress = result.originDetailAddress,
                returnSameAsOrigin = result.returnSameAsOrigin,
                returnZipCode = result.returnZipCode,
                returnRoadAddress = result.returnRoadAddress,
                returnDetailAddress = result.returnDetailAddress,
                jejuExtraFee = result.jejuExtraFee,
                remoteAreaExtraFee = result.remoteAreaExtraFee,
                returnFee = result.returnFee,
                exchangeFee = result.exchangeFee,
                avgDispatchDays = result.avgDispatchDays,
                shippingPolicyText = result.shippingPolicyText,
            )
    }
}
