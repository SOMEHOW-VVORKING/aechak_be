package com.aechak.infra.persistence.product

import com.aechak.domain.product.category.Category
import com.aechak.domain.product.category.enums.CategoryStatus
import com.aechak.domain.product.category.repository.CategoryRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

interface CategoryJpaRepository : JpaRepository<Category, Long> {
    fun findByIdAndStatus(
        id: Long,
        status: CategoryStatus,
    ): Category?

    fun findAllByStatusOrderBySortOrderAscIdAsc(status: CategoryStatus): List<Category>
}

@Repository
class CategoryRepositoryAdapter(
    private val jpaRepository: CategoryJpaRepository,
) : CategoryRepository {
    override fun findActiveById(id: Long): Category? = jpaRepository.findByIdAndStatus(id, CategoryStatus.ACTIVE)

    override fun findAllActiveOrderedBySortOrder(): List<Category> =
        jpaRepository.findAllByStatusOrderBySortOrderAscIdAsc(CategoryStatus.ACTIVE)
}
