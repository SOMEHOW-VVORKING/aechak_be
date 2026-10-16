package com.aechak.infra.persistence.product.option

import com.aechak.domain.product.option.OptionGroup
import com.aechak.domain.product.option.repository.OptionGroupRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

interface OptionGroupJpaRepository : JpaRepository<OptionGroup, Long>

@Repository
class OptionGroupRepositoryAdapter(
    private val jpaRepository: OptionGroupJpaRepository,
) : OptionGroupRepository {
    override fun saveAll(optionGroups: List<OptionGroup>): List<OptionGroup> = jpaRepository.saveAll(optionGroups)
}
