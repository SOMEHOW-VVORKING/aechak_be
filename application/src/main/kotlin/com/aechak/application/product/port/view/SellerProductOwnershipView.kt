package com.aechak.application.product.port.view

/** 소유권 판정용 최소 읽기 모델 — publicId를 내부 id와 소유 셀러로 해석한다 */
data class SellerProductOwnershipView(
    val id: Long,
    val sellerId: Long,
)
