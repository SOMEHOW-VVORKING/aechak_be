package com.aechak.infra.persistence.payment.reconciliation

import com.aechak.domain.payment.reconciliation.ReconciliationRunStatus
import com.aechak.domain.payment.reconciliation.ReconciliationSource
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
import java.time.LocalDate
import java.time.LocalDateTime

@Entity
@Table(
    name = "payment_reconciliation_runs",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_payment_reconciliation_runs_source_base_date", columnNames = ["source", "base_date"]),
    ],
)
class PaymentReconciliationRunJpaEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val source: ReconciliationSource,
    @Column(nullable = false)
    val baseDate: LocalDate,
    @Column(nullable = false)
    val windowFrom: LocalDateTime,
    @Column(nullable = false)
    val windowUntil: LocalDateTime,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val status: ReconciliationRunStatus,
    @Column(nullable = false)
    val attempt: Int,
    @Column(nullable = false)
    val fetchedCount: Int,
    @Column(nullable = false)
    val comparedCount: Int,
    @Column(nullable = false)
    val matchedCount: Int,
    @Column(nullable = false)
    val autoResolvedCount: Int,
    @Column(nullable = false)
    val manualRequiredCount: Int,
    @Column(nullable = false)
    val failedCount: Int,
    @Column(nullable = false)
    val startedAt: LocalDateTime,
    @Column
    val finishedAt: LocalDateTime?,
    @Column(length = 1000)
    val errorMessage: String?,
    @Version
    @Column(nullable = false)
    var version: Int,
) : BaseEntity()
