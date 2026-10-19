package com.aechak.domain.user.point.repository

import com.aechak.domain.user.point.PointTransaction

interface PointTransactionRepository {
    fun save(transaction: PointTransaction): PointTransaction

    fun existsByIdempotencyKey(idempotencyKey: String): Boolean
}
