package com.aechak.domain.settlement.feepolicy

import com.aechak.common.error.BusinessException
import com.aechak.domain.settlement.error.SettlementErrorCode
import com.aechak.domain.support.AggregateRoot
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/**
 * 정산 수수료율 정책. 적용 시작일(effectiveFrom)마다 한 행을 쌓고, 과거 행은 지우지 않는다.
 * sellerId가 null이면 전역 정책이고, 값이 있으면 그 셀러에만 적용된다.
 */
@Entity
@Table(
    name = "settlement_fee_policies",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_settlement_fee_policies_seller_effective",
            columnNames = ["seller_id", "effective_from"],
        ),
    ],
)
class SettlementFeePolicy protected constructor(
    sellerId: Long?,
    effectiveFrom: LocalDate,
    platformFeeRate: BigDecimal,
    pgFeeRate: BigDecimal,
) : AggregateRoot() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    /** null이면 전역 정책(값 참조, seller BC). */
    val sellerId: Long? = sellerId

    @Column(nullable = false)
    val effectiveFrom: LocalDate = effectiveFrom

    @Column(precision = 5, scale = 4, nullable = false)
    val platformFeeRate: BigDecimal = platformFeeRate

    @Column(precision = 5, scale = 4, nullable = false)
    val pgFeeRate: BigDecimal = pgFeeRate

    fun isGlobal(): Boolean = sellerId == null

    fun platformFeeOf(baseAmount: Long): Long = feeOf(baseAmount, platformFeeRate)

    fun pgFeeOf(baseAmount: Long): Long = feeOf(baseAmount, pgFeeRate)

    /** 원 단위 아래는 버린다 — 셀러에게 유리한 쪽으로 떨어진다. */
    private fun feeOf(
        baseAmount: Long,
        rate: BigDecimal,
    ): Long {
        if (baseAmount < 0) {
            throw BusinessException(SettlementErrorCode.INVALID_SETTLEMENT_AMOUNT)
        }
        return BigDecimal
            .valueOf(baseAmount)
            .multiply(rate)
            .setScale(0, RoundingMode.DOWN)
            .toLong()
    }

    companion object {
        fun of(
            sellerId: Long?,
            effectiveFrom: LocalDate,
            platformFeeRate: BigDecimal,
            pgFeeRate: BigDecimal,
        ): SettlementFeePolicy {
            if (!isValidRate(platformFeeRate) || !isValidRate(pgFeeRate)) {
                throw BusinessException(SettlementErrorCode.INVALID_SETTLEMENT_FEE_RATE)
            }
            return SettlementFeePolicy(
                sellerId = sellerId,
                effectiveFrom = effectiveFrom,
                platformFeeRate = platformFeeRate,
                pgFeeRate = pgFeeRate,
            )
        }

        private fun isValidRate(rate: BigDecimal): Boolean = rate >= BigDecimal.ZERO && rate <= BigDecimal.ONE
    }
}
