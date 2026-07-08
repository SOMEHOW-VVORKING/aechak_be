package com.aechak.domain.product.repository

import com.aechak.domain.product.Product

/**
 * product 애그리거트 저장 포트. 구현은 infra 어댑터가 담당한다 —
 * 검색 인프라(Elasticsearch) 도입도 이 포트 뒤 어댑터 추가로 흡수한다.
 * 시그니처는 도메인 타입만 사용한다 — Spring 타입 노출 금지.
 */
interface ProductRepository {
    fun findById(id: Long): Product?
    fun save(product: Product): Product
}
