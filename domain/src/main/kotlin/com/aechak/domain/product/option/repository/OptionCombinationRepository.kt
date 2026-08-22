package com.aechak.domain.product.option.repository

/** 재고 차감/복구는 어댑터의 조건부 원자 UPDATE(WHERE stock_quantity >= ?)로 강제한다. */
interface OptionCombinationRepository {
    /**
     * stock_quantity >= quantity일 때만 원자적으로 차감하고 성공 여부를 돌려준다.
     * 여러 건을 깎을 때 호출측은 id 오름차순으로 호출해 데드락을 예방한다.
     */
    fun deductStock(
        optionCombinationId: Long,
        quantity: Int,
    ): Boolean
}
