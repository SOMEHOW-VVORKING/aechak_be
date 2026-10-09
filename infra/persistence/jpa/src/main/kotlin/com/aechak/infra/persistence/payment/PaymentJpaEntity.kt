package com.aechak.infra.persistence.payment

import com.aechak.domain.payment.enums.PaymentMethod
import com.aechak.domain.payment.enums.PaymentStatus
import com.aechak.domain.support.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import jakarta.persistence.Version

@Entity
@Table(
    name = "payments",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_payments_order_group_id", columnNames = ["order_group_id"]),
        UniqueConstraint(name = "uk_payments_payment_id", columnNames = ["payment_id"]),
        UniqueConstraint(name = "uk_payments_transaction_id", columnNames = ["transaction_id"]),
    ],
)
class PaymentJpaEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long,
    @Column(nullable = false)
    val orderGroupId: Long,
    @Column(name = "payment_id", nullable = false, length = 255)
    val paymentId: String,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val method: PaymentMethod,
    @Column(nullable = false)
    val targetAmount: Long,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    val status: PaymentStatus,
    @Column(length = 255)
    val transactionId: String?,
    @Column
    val realPaidAmount: Long?,
    @Column(length = 100)
    val failureCode: String?,
    @Column
    val cancellableAmount: Long?,
    @Version
    @Column(nullable = false)
    var version: Int,
) : BaseEntity()
