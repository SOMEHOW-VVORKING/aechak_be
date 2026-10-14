package com.aechak.domain.payment.repository

import com.aechak.domain.payment.Payment

interface PaymentRepository {
    fun save(payment: Payment): Payment

    fun findByOrderGroupId(orderGroupId: Long): Payment?
}
