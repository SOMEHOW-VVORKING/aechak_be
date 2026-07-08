package com.aechak.application.product.usecase.result

import com.aechak.domain.product.Product

/** UseCase 반환 전용 모델 골격 — 엔티티 반환 금지. 규칙은 user 템플릿(UserResult) 참조. */
data class ProductResult(
    val productId: Long,
    // TODO: 기능 확정 시 필드 추가
) {
    companion object {
        fun from(product: Product): ProductResult = ProductResult(product.id)
    }
}
