package com.aechak.domain.product.option.event

import com.aechak.domain.support.DomainEvent

data class OptionCombinationChangedEvent(
    val productId: Long,
    val combinationId: Long,
) : DomainEvent
