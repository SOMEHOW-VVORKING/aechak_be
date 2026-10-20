package com.aechak.application.seller.usecase.result

import com.aechak.domain.seller.seller.Seller

/** 배송 정책 조회 결과. 반품지가 출고지와 같으면 반품지 3필드는 null로 내려간다. */
data class ShippingPolicyResult(
    val sellerId: Long,
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
        fun from(seller: Seller): ShippingPolicyResult =
            ShippingPolicyResult(
                sellerId = seller.userId,
                defaultCourierCode = seller.defaultCourierCode,
                baseShippingFee = seller.baseShippingFee,
                freeShippingThreshold = seller.freeShippingThreshold,
                originZipCode = seller.zipCode,
                originRoadAddress = seller.roadAddress,
                originDetailAddress = seller.detailAddress,
                returnSameAsOrigin = seller.returnSameAsOrigin,
                returnZipCode = seller.returnZipCode,
                returnRoadAddress = seller.returnRoadAddress,
                returnDetailAddress = seller.returnDetailAddress,
                jejuExtraFee = seller.jejuExtraFee,
                remoteAreaExtraFee = seller.remoteAreaExtraFee,
                returnFee = seller.returnFee,
                exchangeFee = seller.exchangeFee,
                avgDispatchDays = seller.avgDispatchDays,
                shippingPolicyText = seller.shippingPolicyText,
            )
    }
}
