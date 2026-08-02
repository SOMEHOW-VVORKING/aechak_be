package com.aechak.api.product.category

import com.aechak.api.product.category.response.CategoryResponse
import com.aechak.application.product.category.usecase.CategoryUseCase
import com.aechak.webcommon.response.ApiResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/categories")
class CategoryController(
    private val categoryUseCase: CategoryUseCase,
) {
    @GetMapping
    fun getCategories(): ResponseEntity<ApiResponse<List<CategoryResponse>>> =
        ResponseEntity.ok(
            ApiResponse.of(categoryUseCase.getCategoryTree().map(CategoryResponse::from)),
        )
}
