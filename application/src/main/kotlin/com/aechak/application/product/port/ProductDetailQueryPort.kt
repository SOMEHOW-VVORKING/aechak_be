package com.aechak.application.product.port

import com.aechak.application.product.port.result.ProductCatalogDetailView
import com.aechak.application.product.port.result.ProductImageView

/** 공개 상품 상세 조회 포트(검수 승인 + 판매중/품절 + 셀러 ACTIVE + 카테고리 체인 ACTIVE) */
interface ProductDetailQueryPort {
    fun findVisibleDetail(publicId: String): ProductCatalogDetailView?

    fun findImagesByProductId(productId: Long): List<ProductImageView>
}
