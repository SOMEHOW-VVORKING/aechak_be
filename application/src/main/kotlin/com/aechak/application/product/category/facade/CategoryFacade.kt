package com.aechak.application.product.category.facade

import com.aechak.application.product.category.service.CategoryService
import com.aechak.application.product.category.usecase.CategoryUseCase
import com.aechak.application.product.category.usecase.result.CategoryResult
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CategoryFacade(
    private val categoryService: CategoryService,
) : CategoryUseCase {
    @Transactional(readOnly = true)
    override fun getCategoryTree(): List<CategoryResult> = categoryService.buildActiveTree()
}
