package com.aechak.domain.product.option.repository

import com.aechak.domain.product.option.OptionCombination

/** 재고 차감/복구는 어댑터의 조건부 원자 UPDATE(WHERE stock_quantity >= ?)로 강제한다. */
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

    /**
     * stock_quantity >= quantity일 때만 원자적으로 차감하고 성공 여부를 돌려준다.
     * 여러 건을 깎을 때 호출측은 id 오름차순으로 호출해 데드락을 예방한다.
     */
    fun deductStock(
        optionCombinationId: Long,
        quantity: Int,
    ): Boolean

    /** 조합 행 자체가 없으면 false */
    fun restoreStock(
        optionCombinationId: Long,
        quantity: Int,
    ): Boolean
}
