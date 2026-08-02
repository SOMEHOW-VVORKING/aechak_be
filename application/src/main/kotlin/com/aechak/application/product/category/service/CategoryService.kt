package com.aechak.application.product.category.service

import com.aechak.application.product.category.usecase.result.CategoryResult
import com.aechak.domain.product.category.repository.CategoryRepository
import org.springframework.stereotype.Service

/**
 * 카테고리 조회와 트리 조립 로직. 활성 카테고리를 DB에서 한 번만 읽어(N+1 회피) 루트부터 트리로 조립한다.
 *
 * 조립이 루트(parent == null)에서 연결된 경로로만 내려가므로, 중간 조상이 비활성이라 조회 집합에서 빠지면
 * 그 아래 서브트리는 어떤 루트에서도 도달하지 못해 통째로 누락된다
 * (활성 자식이라도 마찬가지. ProductCatalogQueryAdapter의 노출 정책과 동일).
 */
@Service
class CategoryService(
    private val categoryRepository: CategoryRepository,
) {
    fun buildActiveTree(): List<CategoryResult> {
        val actives = categoryRepository.findAllActiveOrderedBySortOrder()
        val childrenByParentId = actives.groupBy { it.parent?.id }

        fun assemble(parentId: Long?): List<CategoryResult> =
            childrenByParentId[parentId].orEmpty().map { category ->
                CategoryResult.from(category, assemble(category.id))
            }

        return assemble(null)
    }
}
