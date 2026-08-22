package com.aechak.application.product.product.usecase.command

data class ChangeOptionCombinationCommand(
    val sellerId: Long,
    val productPublicId: String,
    val combinationId: Long,
    val stockDelta: Int?,
    val isActive: Boolean?,
)
