package com.aechak.domain.settlement.settlement

import com.aechak.common.error.BusinessException
import com.aechak.domain.settlement.error.SettlementErrorCode
import com.aechak.domain.settlement.feepolicy.SettlementFeePolicy
import com.aechak.domain.settlement.payout.Payout
import com.aechak.domain.settlement.settlement.enums.SettlementStatus
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
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Entity
@Table(
    name = "settlements",
    uniqueConstraints = [UniqueConstraint(name = "uk_settlements_idempotency_key", columnNames = ["idempotency_key"])],
    indexes = [
        Index(name = "idx_settlements_seller_id_month", columnList = "seller_id, settlement_month"),
        Index(name = "idx_settlements_order_id", columnList = "order_id"),
        Index(name = "idx_settlements_status", columnList = "status"),
        Index(name = "idx_settlements_payout_id", columnList = "payout_id"),
    ],
)
class Settlement protected constructor(
    sellerId: Long,
    orderId: Long,
    orderItemId: Long,
    grossAmount: Long,
    shippingFee: Long,
    commission: Long,
    pgFee: Long,
    sellerCouponBurden: Long,
    netAmount: Long,
    platformFeeRate: BigDecimal,
    pgFeeRate: BigDecimal,
    settlementMonth: String,
    expectedPayoutDate: LocalDate,
    idempotencyKey: String,
) : AggregateRoot() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    // 같은 BC의 다른 애그리거트 — FK는 걸지 않고 인덱스만 둔다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payout_id", foreignKey = ForeignKey(ConstraintMode.NO_CONSTRAINT))
    var payout: Payout? = null
        protected set

    val sellerId: Long = sellerId

    /** 주문 단위 집계용 비정규화 값. */
    val orderId: Long = orderId

    val orderItemId: Long = orderItemId

    val grossAmount: Long = grossAmount

    /** 주문의 셀러 배송비를 품목 수만큼 안분한 값. */
    val shippingFee: Long = shippingFee

    val commission: Long = commission

    val pgFee: Long = pgFee

    val sellerCouponBurden: Long = sellerCouponBurden

    var refundDeduction: Long = 0L
        protected set

    var netAmount: Long = netAmount
        protected set

    /** 생성 시점 요율 스냅샷 — 이후 정책이 바뀌어도 과거 정산은 그대로 둔다. */
    @Column(precision = 5, scale = 4, nullable = false)
    val platformFeeRate: BigDecimal = platformFeeRate

    @Column(precision = 5, scale = 4, nullable = false)
    val pgFeeRate: BigDecimal = pgFeeRate

    /** 구매확정일(KST)의 연월. `yyyy-MM` */
    @Column(length = 7, nullable = false)
    val settlementMonth: String = settlementMonth

    @Column(nullable = false)
    val expectedPayoutDate: LocalDate = expectedPayoutDate

    @Enumerated(EnumType.STRING)
    @Column(length = 30, nullable = false)
    var status: SettlementStatus = SettlementStatus.PENDING
        protected set

    @Column(length = 100, nullable = false)
    val idempotencyKey: String = idempotencyKey

    fun markPaid(payout: Payout) {
        transitionTo(SettlementStatus.PAID)
        this.payout = payout
    }

    /**
     * 지급 집행 대상인지 — 정산대기이고, 아직 지급 묶음에 들어가지 않았고, 환불 차감이 없어야 한다.
     * 차감이 생긴 원장은 금액이 더 움직일 수 있어 사람이 확인한 뒤 내보낸다.
     * 지급 묶음 연결 자체는 동시 집행을 선점해야 해서 저장소의 조건부 원자 UPDATE가 맡는다.
     */
    fun isPayoutEligible(): Boolean = status == SettlementStatus.PENDING && payout == null && refundDeduction == 0L

    fun cancel() = transitionTo(SettlementStatus.CANCELLED)

    /** 진행 중 클레임이 있어 지급 대상에서 뺀다. */
    fun hold() = transitionTo(SettlementStatus.ON_HOLD)

    /** 보류 사유가 사라져 다시 지급 대상으로 돌린다. */
    fun release() = transitionTo(SettlementStatus.PENDING)

    /** 클레임 환불 확정분을 차감한다 — 지급 전에만 가능하다. */
    fun applyRefundDeduction(amount: Long) {
        if (status != SettlementStatus.PENDING && status != SettlementStatus.ON_HOLD) {
            throw BusinessException(SettlementErrorCode.SETTLEMENT_NOT_PENDING)
        }
        // 이 품목으로 받은 돈(상품금액 + 안분 배송비)을 넘는 차감은 막는다.
        // 수수료보다 큰 차감이 들어오면 net_amount는 음수가 될 수 있다 — 셀러가 돌려줄 몫이다.
        if (amount < 0 || refundDeduction + amount > grossAmount + shippingFee) {
            throw BusinessException(SettlementErrorCode.INVALID_SETTLEMENT_AMOUNT)
        }
        refundDeduction += amount
        netAmount -= amount
    }

    private fun transitionTo(next: SettlementStatus) {
        if (!status.canTransitionTo(next)) {
            throw BusinessException(SettlementErrorCode.INVALID_SETTLEMENT_STATUS_TRANSITION)
        }
        status = next
    }

    companion object {
        /** 지급 예정일은 구매확정일 기준 역일로 더한다 — 공휴일 테이블이 없다. */
        const val PAYOUT_DELAY_DAYS = 5L

        /** 주문품목 하나당 원장 1행. 확정이 다시 불려도 키가 같아 UNIQUE가 두 번째 행을 막는다. */
        const val IDEMPOTENCY_KEY_PREFIX = "SETTLE:ORDER_ITEM:"

        private val MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM")

        fun settlementMonthOf(confirmedOn: LocalDate): String = MONTH_FORMATTER.format(confirmedOn)

        fun expectedPayoutDateOf(confirmedOn: LocalDate): LocalDate = confirmedOn.plusDays(PAYOUT_DELAY_DAYS)

        fun idempotencyKeyOf(orderItemId: Long): String = "$IDEMPOTENCY_KEY_PREFIX$orderItemId"

        /**
         * 구매확정된 주문품목 하나의 원장을 요율 정책으로 계산해 만든다.
         * 수수료 과세 기준금액은 상품금액(할인 안분 차감 후)이고 배송비는 기준에서 뺀다 —
         * 배송비는 net_amount에 그대로 더해 셀러에게 실비로 넘기는 돈이라, 거기에 요율을 매기면 실비 전달이 깨진다.
         */
        fun createOnPurchaseConfirm(
            feePolicy: SettlementFeePolicy,
            sellerId: Long,
            orderId: Long,
            orderItemId: Long,
            grossAmount: Long,
            shippingFee: Long,
            sellerCouponBurden: Long,
            confirmedOn: LocalDate,
        ): Settlement {
            val feeBase = grossAmount
            return create(
                sellerId = sellerId,
                orderId = orderId,
                orderItemId = orderItemId,
                grossAmount = grossAmount,
                shippingFee = shippingFee,
                commission = feePolicy.platformFeeOf(feeBase),
                pgFee = feePolicy.pgFeeOf(feeBase),
                sellerCouponBurden = sellerCouponBurden,
                platformFeeRate = feePolicy.platformFeeRate,
                pgFeeRate = feePolicy.pgFeeRate,
                confirmedOn = confirmedOn,
                idempotencyKey = idempotencyKeyOf(orderItemId),
            )
        }

        /**
         * 구매확정된 주문품목 하나의 정산 원장 1행을 만든다.
         * 정산월과 지급 예정일은 구매확정일(KST)에서 직접 뽑아 호출자가 어긋난 값을 넣을 수 없게 한다.
         */
        fun create(
            sellerId: Long,
            orderId: Long,
            orderItemId: Long,
            grossAmount: Long,
            shippingFee: Long,
            commission: Long,
            pgFee: Long,
            sellerCouponBurden: Long,
            platformFeeRate: BigDecimal,
            pgFeeRate: BigDecimal,
            confirmedOn: LocalDate,
            idempotencyKey: String,
        ): Settlement {
            if (grossAmount < 0 || shippingFee < 0 || commission < 0 || pgFee < 0 || sellerCouponBurden < 0) {
                throw BusinessException(SettlementErrorCode.INVALID_SETTLEMENT_AMOUNT)
            }
            // 스냅샷 컬럼도 DECIMAL(5,4)다 — 더 긴 요율을 받으면 저장될 때 반올림돼 원장이 근거를 잃는다.
            if (!SettlementFeePolicy.isStorableRate(platformFeeRate) || !SettlementFeePolicy.isStorableRate(pgFeeRate)) {
                throw BusinessException(SettlementErrorCode.INVALID_SETTLEMENT_FEE_RATE)
            }
            val netAmount = grossAmount + shippingFee - commission - pgFee - sellerCouponBurden
            return Settlement(
                sellerId = sellerId,
                orderId = orderId,
                orderItemId = orderItemId,
                grossAmount = grossAmount,
                shippingFee = shippingFee,
                commission = commission,
                pgFee = pgFee,
                sellerCouponBurden = sellerCouponBurden,
                netAmount = netAmount,
                platformFeeRate = platformFeeRate,
                pgFeeRate = pgFeeRate,
                settlementMonth = settlementMonthOf(confirmedOn),
                expectedPayoutDate = expectedPayoutDateOf(confirmedOn),
                idempotencyKey = idempotencyKey,
            )
        }
    }
}
