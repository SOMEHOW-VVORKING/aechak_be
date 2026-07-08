package com.aechak.domain.payment.repository

import com.aechak.domain.payment.Payment

/**
 * payment 애그리거트 저장 포트. payment는 L3라 어댑터가 순수 모델 ↔ JPA 영속 모델 매핑까지 책임진다.
 * 시그니처는 도메인 타입만 사용한다 — Spring 타입 노출 금지.
 */
interface PaymentRepository {
    fun findById(id: Long): Payment?
    fun save(payment: Payment): Payment
}
