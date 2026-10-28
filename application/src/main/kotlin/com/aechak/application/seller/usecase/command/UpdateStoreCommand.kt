package com.aechak.application.seller.usecase.command

/**
 * 스토어 설정 전체 덮어쓰기 입력. 이미지 키는 승격 전 tmp 키이거나 이미 저장된 키이고,
 * 주소 3필드는 배송 정책의 출고지와 같은 컬럼으로 간다.
 */
data class UpdateStoreCommand(
    val sellerId: Long,
    val storeName: String,
    val brandIntro: String?,
    val profileImageKey: String?,
    val bannerImageKey: String?,
    val zipCode: String?,
    val roadAddress: String?,
    val detailAddress: String?,
)
