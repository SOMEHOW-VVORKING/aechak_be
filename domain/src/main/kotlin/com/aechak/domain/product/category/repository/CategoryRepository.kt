package com.aechak.domain.product.category.repository

import com.aechak.domain.product.category.Category

interface CategoryRepository {
    fun findActiveById(id: Long): Category?

    fun findAllActiveOrderedBySortOrder(): List<Category>
}
