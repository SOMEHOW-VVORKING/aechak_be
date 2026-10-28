package com.aechak.domain.settlement.batch

import com.aechak.common.error.BusinessException
import com.aechak.domain.settlement.batch.enums.SettlementBatchStatus
import com.aechak.domain.settlement.error.SettlementErrorCode
import com.aechak.domain.support.AggregateRoot
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDate

@Entity
@Table(
    name = "settlement_execution_batches",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_settlement_execution_batches_base_date", columnNames = ["settlement_base_date"]),
    ],
)
class SettlementExecutionBatch protected constructor(
    settlementBaseDate: LocalDate,
    totalTargetCount: Int,
    totalAmount: Long,
) : AggregateRoot() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    @Column(nullable = false)
    val settlementBaseDate: LocalDate = settlementBaseDate

    val totalTargetCount: Int = totalTargetCount

    val totalAmount: Long = totalAmount

    @Enumerated(EnumType.STRING)
    @Column(length = 30, nullable = false)
    var status: SettlementBatchStatus = SettlementBatchStatus.GENERATED
        protected set

    /** 종결된 회차에는 지급 건을 더 붙이지 않는다 — 입금 반영이 끝난 회차에 새 송금 요청이 섞이면 회차 집계가 거짓이 된다. */
    fun acceptsPayout(): Boolean = status != SettlementBatchStatus.COMPLETED

    fun startProcessing() = transitionTo(SettlementBatchStatus.PROCESSING)

    fun complete() = transitionTo(SettlementBatchStatus.COMPLETED)

    private fun transitionTo(next: SettlementBatchStatus) {
        if (!status.canTransitionTo(next)) {
            throw BusinessException(SettlementErrorCode.INVALID_SETTLEMENT_BATCH_STATUS_TRANSITION)
        }
        status = next
    }

    companion object {
        fun generate(
            settlementBaseDate: LocalDate,
            totalTargetCount: Int,
            totalAmount: Long,
        ): SettlementExecutionBatch =
            SettlementExecutionBatch(
                settlementBaseDate = settlementBaseDate,
                totalTargetCount = totalTargetCount,
                totalAmount = totalAmount,
            )
    }
}
