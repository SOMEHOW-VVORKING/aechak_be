package com.aechak.infra.persistence.payment.reconciliation

import com.aechak.domain.payment.reconciliation.MismatchType
import com.aechak.domain.payment.reconciliation.ReconciliationResolution
import com.aechak.domain.payment.reconciliation.ReconciliationSource
import com.aechak.domain.support.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDateTime

@Entity
@Table(
    name = "payment_reconciliation_items",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_payment_reconciliation_items_run_payment_key", columnNames = ["run_id", "payment_key"]),
    ],
    indexes = [
        Index(name = "idx_payment_reconciliation_items_resolution", columnList = "resolution, mismatch_type"),
    ],
)
class PaymentReconciliationItemJpaEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long,
    @Column(nullable = false)
    val runId: Long,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val source: ReconciliationSource,
    @Column(nullable = false, length = 255)
    val paymentKey: String,
    @Column
    val paymentId: Long?,
    @Column
    val orderGroupId: Long?,
    @Column(length = 30)
    val ourPaymentStatus: String?,
    @Column(length = 30)
    val ourGroupStatus: String?,
    @Column
    val ourGroupFinalAmount: Long?,
    @Column
    val ourTargetAmount: Long?,
    @Column
    val ourPaidAmount: Long?,
    @Column
    val ourCancellableAmount: Long?,
    @Column(length = 30)
    val pgStatus: String?,
    @Column
    val pgPaidAmount: Long?,
    @Column
    val pgCancelledAmount: Long?,
    @Column(length = 255)
    val pgTxId: String?,
    @Column
    val pgStatusChangedAt: LocalDateTime?,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    val mismatchType: MismatchType,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    val resolution: ReconciliationResolution,
    @Column(length = 500)
    val resolutionNote: String?,
    @Column
    val resolvedAt: LocalDateTime?,
) : BaseEntity()
