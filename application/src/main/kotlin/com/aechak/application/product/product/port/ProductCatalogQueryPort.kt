package com.aechak.application.product.product.port

import com.aechak.application.product.product.port.view.ProductCatalogView
import java.time.LocalDateTime

/** 공개 카탈로그 목록 조회 포트 (상품, 카테고리 계층, 셀러 상태를 조합하는 교차 BC read projection) */
interface ProductCatalogQueryPort {
    fun findVisiblePage(condition: ProductCatalogCondition): List<ProductCatalogView>

    fun countVisible(categoryId: Long?): Long

    /** 노출 상품의 내부 id만 조회(상세 전체 로딩 회피). */
    fun findVisibleIdByPublicId(publicId: String): Long?

    /** 인기순 상위 목록 */
    fun findPopular(
        limit: Int,
        now: LocalDateTime,
    ): List<ProductCatalogView>

    /** 판매중 상품에서 뽑은 무작위 상품(임시 추천) */
    fun findRandomOnSale(
        limit: Int,
        now: LocalDateTime,
    ): List<ProductCatalogView>
}
