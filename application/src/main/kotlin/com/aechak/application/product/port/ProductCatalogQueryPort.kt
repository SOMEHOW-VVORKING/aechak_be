package com.aechak.application.product.port

import com.aechak.application.product.port.result.ProductCatalogView

/** 공개 카탈로그 목록 조회 포트 (상품, 카테고리 계층, 셀러 상태를 조합하는 교차 BC read projection) */
interface ProductCatalogQueryPort {
    fun findVisiblePage(condition: ProductCatalogCondition): List<ProductCatalogView>

    fun countVisible(categoryId: Long?): Long

    /** 커서 keyset 해석용 내부 id 변환 */
    fun findIdByPublicId(publicId: String): Long?
}
