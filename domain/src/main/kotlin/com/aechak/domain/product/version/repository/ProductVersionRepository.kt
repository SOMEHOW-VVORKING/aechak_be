package com.aechak.domain.product.version.repository

import com.aechak.domain.product.version.ProductVersion

/** append-only 스냅샷. */
interface ProductVersionRepository {
    fun save(productVersion: ProductVersion): ProductVersion
}
