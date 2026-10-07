package com.aechak.domain.product.version.repository

import com.aechak.domain.product.version.ProductVersion

/** append-only 스냅샷. */
interface ProductVersionRepository {
    fun save(productVersion: ProductVersion): ProductVersion

    /** 아직 버전이 없으면 null. */
    fun findLastVersionNo(productId: Long): Int?
}
