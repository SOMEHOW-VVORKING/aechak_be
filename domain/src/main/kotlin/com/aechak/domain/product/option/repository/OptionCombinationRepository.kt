package com.aechak.domain.product.option.repository

import com.aechak.domain.product.option.OptionCombination

/** 재고 차감/복구는 어댑터의 조건부 원자 UPDATE(WHERE stock_quantity >= ?)로 강제한다. */
interface OptionCombinationRepository {
    fun saveAll(optionCombinations: List<OptionCombination>): List<OptionCombination>
}
