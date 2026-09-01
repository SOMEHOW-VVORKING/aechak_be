package com.aechak.application.order.service.model

data class CancelledOrderGroup(
    val publicId: String,
    val buyerId: Long,
    val usedPoint: Long,
    val items: List<Item>,
) {
    data class Item(
        val optionCombinationId: Long,
        val quantity: Int,
    )
}
