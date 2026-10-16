package com.aechak.domain.payment.repository

import com.aechak.domain.payment.Payment

interface PaymentRepository {
    fun save(payment: Payment): Payment

    fun findByOrderGroupId(orderGroupId: Long): Payment?

    /** 웹훅이 알려주는 식별자로 찾는 경로. paymentId는 UNIQUE라 결과는 0 또는 1행 */
    fun findByPaymentId(paymentId: String): Payment?
}
