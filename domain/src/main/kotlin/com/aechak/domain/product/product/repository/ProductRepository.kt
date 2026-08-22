package com.aechak.domain.product.product.repository

import com.aechak.domain.product.product.Product

/** 검색 인프라(Elasticsearch)도 이 포트 뒤 어댑터로 흡수한다. */
interface ProductRepository {
    fun save(product: Product): Product

    /** 커밋을 기다리지 않고 저장을 즉시 반영함. */
    fun saveNow(product: Product): Product

    /**
     * 재고에서 파생하는 판매 상태를 고칠 때 쓰는 조회. 행을 잠가 읽어야 판정과 반영 사이에
     * 다른 판정이 끼어들지 못함. 상태를 안 바꾸고 끝난 판정은 아무것도 안 써서 @Version이 못 거름.
     */
    fun findByIdForUpdate(id: Long): Product?

    fun findByPublicIdAndSellerId(
        publicId: String,
        sellerId: Long,
    ): Product?

    fun findIdByPublicId(publicId: String): Long?
}
