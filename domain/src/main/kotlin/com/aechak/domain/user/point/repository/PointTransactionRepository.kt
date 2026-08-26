package com.aechak.domain.user.point.repository

import com.aechak.domain.user.point.PointTransaction

interface PointTransactionRepository {
    fun save(pointTransaction: PointTransaction): PointTransaction
}
