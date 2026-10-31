package com.aechak.application.product.product.usecase.result

import com.aechak.application.product.product.port.view.ProductRefView

/**
 * 공개 상품의 식별 정보. 상세 전체를 끌고 오지 않고 id·셀러·상품명만 돌려준다 —
 * 다른 BC(상품 문의 등)가 접수 시점 값을 자기 행에 복사할 때 쓴다.
 */
data class ProductRefResult(
    val productId: Long,
    val productPublicId: String,
    val sellerId: Long,
    val name: String,
) {
    companion object {
        fun from(view: ProductRefView): ProductRefResult =
            ProductRefResult(
                productId = view.id,
                productPublicId = view.publicId,
                sellerId = view.sellerId,
                name = view.name,
            )
    }
}
