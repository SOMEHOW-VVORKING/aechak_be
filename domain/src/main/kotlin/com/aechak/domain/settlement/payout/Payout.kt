package com.aechak.domain.settlement.payout

import com.aechak.common.error.BusinessException
import com.aechak.domain.settlement.batch.SettlementExecutionBatch
import com.aechak.domain.settlement.error.SettlementErrorCode
import com.aechak.domain.settlement.payout.enums.PayoutStatus
import com.aechak.domain.support.AggregateRoot
import jakarta.persistence.Column
import jakarta.persistence.ConstraintMode
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.ForeignKey
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDateTime

@Entity
@Table(
    name = "payouts",
    uniqueConstraints = [UniqueConstraint(name = "uk_payouts_transfer_key", columnNames = ["transfer_key"])],
    indexes = [
        Index(name = "idx_payouts_seller_id_status", columnList = "seller_id, status"),
        Index(name = "idx_payouts_batch_id", columnList = "batch_id"),
    ],
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

    // 같은 BC의 다른 애그리거트 — FK는 걸지 않고 인덱스만 둔다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id", nullable = false, foreignKey = ForeignKey(ConstraintMode.NO_CONSTRAINT))
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

    /** 송금 요청을 보냈다. 지급사 식별자를 함께 적는다. */
    fun markSent(portonePayoutId: String?) {
        transitionTo(PayoutStatus.SENT)
        this.portonePayoutId = portonePayoutId
        failReason = null
        processedAt = LocalDateTime.now()
    }

    /** 입금이 확인됐다. */
    fun markPaid(depositedAt: LocalDateTime) {
        transitionTo(PayoutStatus.PAID)
        this.depositedAt = depositedAt
    }

    fun markFailed(reason: String) {
        transitionTo(PayoutStatus.FAILED)
        failReason = reason.take(FAIL_REASON_MAX_LENGTH)
        processedAt = LocalDateTime.now()
    }

    private fun transitionTo(next: PayoutStatus) {
        if (!status.canTransitionTo(next)) {
            throw BusinessException(SettlementErrorCode.INVALID_PAYOUT_STATUS_TRANSITION)
        }
        status = next
    }

    companion object {
        private const val FAIL_REASON_MAX_LENGTH = 255

        /** 배치·셀러 조합 하나당 지급 1건. 배치가 다시 돌아도 키가 같아 UNIQUE가 두 번째 행을 막는다. */
        const val TRANSFER_KEY_PREFIX = "PAYOUT:"

        fun transferKeyOf(
            batchId: Long,
            sellerId: Long,
        ): String = "$TRANSFER_KEY_PREFIX$batchId:$sellerId"

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
