package com.aechak.domain.product.option.repository

import com.aechak.domain.product.option.OptionGroup

interface OptionGroupRepository {
    fun saveAll(optionGroups: List<OptionGroup>): List<OptionGroup>

    /** 등록 시 입력 순서(sortOrder)대로 돌려준다. */
    fun findByProductId(productId: Long): List<OptionGroup>
}
