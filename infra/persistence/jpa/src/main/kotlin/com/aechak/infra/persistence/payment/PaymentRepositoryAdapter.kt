package com.aechak.infra.persistence.payment

import com.aechak.domain.payment.Payment
import com.aechak.domain.payment.repository.PaymentRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

interface PaymentJpaRepository : JpaRepository<PaymentJpaEntity, Long> {
    fun findByOrderGroupId(orderGroupId: Long): PaymentJpaEntity?

    fun findByPaymentId(paymentId: String): PaymentJpaEntity?

    @Query(
        "select p.paymentId from PaymentJpaEntity p " +
            "where p.updatedAt >= :from and p.updatedAt < :until " +
            "order by p.id",
    )
    fun findPaymentIdsUpdatedBetween(
        @Param("from") from: LocalDateTime,
        @Param("until") until: LocalDateTime,
    ): List<String>
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

    override fun findByPaymentId(paymentId: String): Payment? =
        jpaRepository
            .findByPaymentId(paymentId)
            ?.let(PaymentMapper::toDomain)

    override fun findPaymentIdsUpdatedBetween(
        from: LocalDateTime,
        until: LocalDateTime,
    ): List<String> = jpaRepository.findPaymentIdsUpdatedBetween(from, until)
}
