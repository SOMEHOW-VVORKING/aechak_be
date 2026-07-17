package com.aechak.domain.settlement.payout

import com.aechak.domain.settlement.batch.SettlementExecutionBatch
import com.aechak.domain.settlement.payout.enums.PayoutStatus
import com.aechak.domain.support.AggregateRoot
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDateTime

@Entity
@Table(
    name = "payouts",
    uniqueConstraints = [UniqueConstraint(name = "uk_payouts_transfer_key", columnNames = ["transfer_key"])],
)
class Payout protected constructor(
    batch: SettlementExecutionBatch,
    sellerId: Long,
    settlementTargetAmount: Long,
    transferKey: String,
) : AggregateRoot() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id", nullable = false)
    val batch: SettlementExecutionBatch = batch

    val sellerId: Long = sellerId

    val settlementTargetAmount: Long = settlementTargetAmount

    @Column(length = 100, nullable = false)
    val transferKey: String = transferKey

    @Column(length = 100)
    var portonePayoutId: String? = null
        protected set

    @Enumerated(EnumType.STRING)
    @Column(length = 30, nullable = false)
    var status: PayoutStatus = PayoutStatus.PENDING
        protected set

    @Column(length = 255)
    var failReason: String? = null
        protected set

    var processedAt: LocalDateTime? = null
        protected set

    var depositedAt: LocalDateTime? = null
        protected set

    companion object {
        fun create(
            batch: SettlementExecutionBatch,
            sellerId: Long,
            settlementTargetAmount: Long,
            transferKey: String,
        ): Payout =
            Payout(
                batch = batch,
                sellerId = sellerId,
                settlementTargetAmount = settlementTargetAmount,
                transferKey = transferKey,
            )
    }
}
