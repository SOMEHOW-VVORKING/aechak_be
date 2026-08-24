package com.aechak.domain.product.option.repository

import com.aechak.domain.product.option.OptionGroup

interface OptionGroupRepository {
    fun saveAll(optionGroups: List<OptionGroup>): List<OptionGroup>
}
