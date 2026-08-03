package com.aechak.application.product.category.usecase

import com.aechak.application.product.category.usecase.result.CategoryResult

interface CategoryUseCase {
    fun getCategoryTree(): List<CategoryResult>
}
