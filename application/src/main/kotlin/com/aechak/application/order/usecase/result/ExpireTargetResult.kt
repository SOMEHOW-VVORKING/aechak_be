package com.aechak.application.order.usecase.result

import java.time.LocalDateTime

data class ExpireTargetResult(
    val orderGroupId: Long,
    val orderGroupPublicId: String,
    val expiresAt: LocalDateTime,
)
