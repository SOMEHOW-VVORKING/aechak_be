package com.aechak.application.product.product.port.view

/** 노출 상품의 식별 정보만 담은 읽기 모델 — 다른 BC가 접수 시점 값을 복사해 갈 때 쓴다. */
data class ProductRefView(
    val id: Long,
    val publicId: String,
    val sellerId: Long,
    val name: String,
)
