package com.aechak.application.user.point.usecase.command

data class ReleasePointCommand(
    val userId: Long,
    val amount: Long,
    val idempotencyKey: String,
    val sourceType: String? = null,
    val sourceId: Long? = null,
)
