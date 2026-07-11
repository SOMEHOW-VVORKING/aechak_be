package com.aechak.domain.settlement.settlement

import com.aechak.common.error.BusinessException
import com.aechak.domain.settlement.error.SettlementErrorCode
import com.aechak.domain.settlement.payout.Payout
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
import com.aechak.domain.settlement.settlement.enums.SettlementStatus

@Entity
@Table(
    name = "settlements",
    uniqueConstraints = [UniqueConstraint(name = "uk_settlements_idempotency_key", columnNames = ["idempotency_key"])],
)
class Settlement protected constructor(
    sellerId: Long,
    orderItemId: Long,
    grossAmount: Long,
    commission: Long,
    pgFee: Long,
    sellerCouponBurden: Long,
    netAmount: Long,
    idempotencyKey: String,
) : AggregateRoot() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payout_id")
    var payout: Payout? = null
        protected set

    val sellerId: Long = sellerId

    val orderItemId: Long = orderItemId

    val grossAmount: Long = grossAmount

    val commission: Long = commission

    val pgFee: Long = pgFee

    val sellerCouponBurden: Long = sellerCouponBurden

    val netAmount: Long = netAmount

    @Enumerated(EnumType.STRING)
    @Column(length = 30, nullable = false)
    var status: SettlementStatus = SettlementStatus.PENDING
        protected set

    @Column(length = 100, nullable = false)
    val idempotencyKey: String = idempotencyKey

    fun markPaid(payout: Payout) {
        if (status != SettlementStatus.PENDING) {
            throw BusinessException(SettlementErrorCode.SETTLEMENT_NOT_PENDING)
        }
        this.payout = payout
        status = SettlementStatus.PAID
    }

    fun cancel() {
        if (status != SettlementStatus.PENDING) {
            throw BusinessException(SettlementErrorCode.SETTLEMENT_NOT_PENDING)
        }
        status = SettlementStatus.CANCELLED
    }

    companion object {
        fun create(
            sellerId: Long,
            orderItemId: Long,
            grossAmount: Long,
            commission: Long,
            pgFee: Long,
            sellerCouponBurden: Long,
            idempotencyKey: String,
        ): Settlement {
            if (grossAmount < 0 || commission < 0 || pgFee < 0 || sellerCouponBurden < 0) {
                throw BusinessException(SettlementErrorCode.INVALID_SETTLEMENT_AMOUNT)
            }
            val netAmount = grossAmount - commission - pgFee - sellerCouponBurden
            return Settlement(
                sellerId = sellerId,
                orderItemId = orderItemId,
                grossAmount = grossAmount,
                commission = commission,
                pgFee = pgFee,
                sellerCouponBurden = sellerCouponBurden,
                netAmount = netAmount,
                idempotencyKey = idempotencyKey,
            )
        }
    }
}
