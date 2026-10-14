package com.aechak.infra.persistence.payment

import com.aechak.domain.payment.Payment
import com.aechak.domain.payment.repository.PaymentRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

interface PaymentJpaRepository : JpaRepository<PaymentJpaEntity, Long> {
    fun findByOrderGroupId(orderGroupId: Long): PaymentJpaEntity?
}

@Repository
class PaymentRepositoryAdapter(
    private val jpaRepository: PaymentJpaRepository,
) : PaymentRepository {
    override fun save(payment: Payment): Payment {
        val saved = jpaRepository.save(PaymentMapper.toEntity(payment))
        return PaymentMapper.toDomain(saved)
    }

    override fun findByOrderGroupId(orderGroupId: Long): Payment? =
        jpaRepository
            .findByOrderGroupId(orderGroupId)
            ?.let(PaymentMapper::toDomain)
}
