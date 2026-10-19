package com.aechak.application.seller.usecase.command

/** 배송 정책 전체 덮어쓰기 입력. 출고지 3필드는 셀러의 주소 컬럼(zipCode/roadAddress/detailAddress)으로 간다. */
data class UpdateShippingPolicyCommand(
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
)
