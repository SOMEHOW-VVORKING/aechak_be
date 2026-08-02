package com.aechak.api.product.category.response

import com.aechak.application.product.category.usecase.result.CategoryResult

/** 카테고리 트리 응답 노드 */
data class CategoryResponse(
    val categoryId: Long,
    val name: String,
    val iconUrl: String?,
    val sortOrder: Int,
    val children: List<CategoryResponse>,
) {
    companion object {
        fun from(result: CategoryResult): CategoryResponse =
            CategoryResponse(
                categoryId = result.categoryId,
                name = result.name,
                iconUrl = result.iconUrl,
                sortOrder = result.sortOrder,
                children = result.children.map(::from),
            )
    }
}
