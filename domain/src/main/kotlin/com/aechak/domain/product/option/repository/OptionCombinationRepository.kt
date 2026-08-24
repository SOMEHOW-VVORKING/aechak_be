package com.aechak.domain.product.option.repository

import com.aechak.domain.product.option.OptionCombination

interface OptionCombinationRepository {
    fun saveAll(optionCombinations: List<OptionCombination>): List<OptionCombination>

    /** 커밋을 기다리지 않고 저장을 즉시 반영함. */
    fun saveNow(optionCombination: OptionCombination): OptionCombination

    fun findById(id: Long): OptionCombination?

    fun findByIdAndProductIdForUpdate(
        id: Long,
        productId: Long,
    ): OptionCombination?

    fun existsActiveStock(productId: Long): Boolean
}
