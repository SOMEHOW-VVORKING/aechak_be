package com.aechak.infra.persistence.product.option

import com.aechak.domain.product.option.OptionGroup
import com.aechak.domain.product.option.repository.OptionGroupRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

interface OptionGroupJpaRepository : JpaRepository<OptionGroup, Long> {
    fun findByProductIdOrderBySortOrderAsc(productId: Long): List<OptionGroup>
}

@Repository
class OptionGroupRepositoryAdapter(
    private val jpaRepository: OptionGroupJpaRepository,
) : OptionGroupRepository {
    override fun saveAll(optionGroups: List<OptionGroup>): List<OptionGroup> = jpaRepository.saveAll(optionGroups)

    override fun findByProductId(productId: Long): List<OptionGroup> = jpaRepository.findByProductIdOrderBySortOrderAsc(productId)
}
