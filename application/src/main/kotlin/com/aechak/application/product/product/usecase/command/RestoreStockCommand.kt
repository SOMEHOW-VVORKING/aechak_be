package com.aechak.application.product.product.usecase.command

data class RestoreStockCommand(
    val items: List<Item>,
) {
    data class Item(
        val optionCombinationId: Long,
        val quantity: Int,
    )
}
