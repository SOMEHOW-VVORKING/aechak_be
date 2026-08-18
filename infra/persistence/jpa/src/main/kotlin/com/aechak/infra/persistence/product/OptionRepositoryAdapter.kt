package com.aechak.infra.persistence.product

import com.aechak.domain.product.option.OptionCombination
import com.aechak.domain.product.option.OptionGroup
import com.aechak.domain.product.option.repository.OptionCombinationRepository
import com.aechak.domain.product.option.repository.OptionGroupRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

interface OptionGroupJpaRepository : JpaRepository<OptionGroup, Long>

interface OptionCombinationJpaRepository : JpaRepository<OptionCombination, Long>

@Repository
class OptionGroupRepositoryAdapter(
    private val jpaRepository: OptionGroupJpaRepository,
) : OptionGroupRepository {
    override fun saveAll(optionGroups: List<OptionGroup>): List<OptionGroup> = jpaRepository.saveAll(optionGroups)
}

@Repository
class OptionCombinationRepositoryAdapter(
    private val jpaRepository: OptionCombinationJpaRepository,
) : OptionCombinationRepository {
    override fun saveAll(optionCombinations: List<OptionCombination>): List<OptionCombination> = jpaRepository.saveAll(optionCombinations)
}
