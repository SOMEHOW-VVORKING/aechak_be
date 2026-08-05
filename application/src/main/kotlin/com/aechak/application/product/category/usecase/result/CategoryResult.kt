package com.aechak.application.product.category.usecase.result

import com.aechak.domain.product.category.Category

/** 카테고리 트리 노드 결과 */
data class CategoryResult(
    val categoryId: Long,
    val name: String,
    val iconUrl: String?,
    val sortOrder: Int,
    val children: List<CategoryResult>,
) {
    companion object {
        fun from(
            category: Category,
            children: List<CategoryResult>,
        ): CategoryResult =
            CategoryResult(
                categoryId = category.id,
                name = category.name,
                iconUrl = category.iconUrl,
                sortOrder = category.sortOrder,
                children = children,
            )
    }
}
