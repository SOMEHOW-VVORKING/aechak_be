package com.aechak.domain.product.product.repository

import com.aechak.domain.product.product.Product

/** 검색 인프라(Elasticsearch)도 이 포트 뒤 어댑터로 흡수한다. */
interface ProductRepository {
    fun save(product: Product): Product
}
