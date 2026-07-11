package com.aechak.domain.settlement.batch

import com.aechak.domain.support.AggregateRoot
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDate
import com.aechak.domain.settlement.batch.enums.SettlementBatchStatus

@Entity
@Table(name = "settlement_execution_batches")
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

    companion object {
        fun generate(
            settlementBaseDate: LocalDate,
            totalTargetCount: Int,
            totalAmount: Long,
        ): SettlementExecutionBatch {
            return SettlementExecutionBatch(
                settlementBaseDate = settlementBaseDate,
                totalTargetCount = totalTargetCount,
                totalAmount = totalAmount,
            )
        }
    }
}
