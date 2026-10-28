package com.aechak.api.settlement

import com.aechak.api.support.IntegrationTestBase
import com.aechak.domain.settlement.batch.SettlementExecutionBatch
import com.aechak.domain.settlement.batch.repository.SettlementExecutionBatchRepository
import com.aechak.domain.settlement.feepolicy.SettlementFeePolicy
import com.aechak.domain.settlement.feepolicy.repository.SettlementFeePolicyRepository
import com.aechak.domain.settlement.payout.Payout
import com.aechak.domain.settlement.payout.repository.PayoutRepository
import com.aechak.domain.settlement.settlement.Settlement
import com.aechak.domain.settlement.settlement.repository.SettlementRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.math.BigDecimal
import java.time.LocalDate

/**
 * 정산 저장소 어댑터의 조회 계약 통합 검증. 깨지면 원장 생성이 중복을 못 걸러내거나,
 * 정산에 적용할 요율을 엉뚱한 정책 행에서 집어 온다.
 */
class SettlementRepositoryIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var settlementRepository: SettlementRepository

    @Autowired
    private lateinit var payoutRepository: PayoutRepository

    @Autowired
    private lateinit var batchRepository: SettlementExecutionBatchRepository

    @Autowired
    private lateinit var feePolicyRepository: SettlementFeePolicyRepository

    @Test
    fun `멱등키로 정산 원장을 되찾는다`() {
        tx.executeWithoutResult { settlementRepository.saveAll(listOf(settlement("SETTLE:ORDER_ITEM:1"))) }

        val found = tx.execute { settlementRepository.findByIdempotencyKey("SETTLE:ORDER_ITEM:1") }

        assertThat(found).isNotNull()
        assertThat(found!!.netAmount).isEqualTo(92_200L)
        assertThat(found.settlementMonth).isEqualTo("2026-03")
        assertThat(found.expectedPayoutDate).isEqualTo(LocalDate.of(2026, 3, 15))
        assertThat(settlementRepository.findByIdempotencyKey("SETTLE:ORDER_ITEM:없음")).isNull()
    }

    @Test
    fun `송금키로 지급을, 기준일로 집행 배치를 되찾는다`() {
        val baseDate = LocalDate.of(2026, 3, 20)
        tx.executeWithoutResult {
            val batch = batchRepository.save(SettlementExecutionBatch.generate(baseDate, 1, 92_200L))
            payoutRepository.save(Payout.create(batch, SELLER_ID, 92_200L, "PAYOUT:${batch.id}:$SELLER_ID"))
        }

        val batch = tx.execute { batchRepository.findBySettlementBaseDate(baseDate) }
        assertThat(batch).isNotNull()

        val payout = tx.execute { payoutRepository.findByTransferKey("PAYOUT:${batch!!.id}:$SELLER_ID") }
        assertThat(payout).isNotNull()
        assertThat(payout!!.settlementTargetAmount).isEqualTo(92_200L)
        assertThat(batchRepository.findBySettlementBaseDate(baseDate.plusDays(1))).isNull()
    }

    @Test
    fun `기준일에 적용할 정책은 적용 시작일이 지난 것 중 가장 늦은 행이다`() {
        tx.executeWithoutResult {
            em.persist(policy(null, LocalDate.of(2026, 1, 1), "0.0800"))
            em.persist(policy(null, LocalDate.of(2026, 6, 1), "0.0700"))
        }

        assertThat(effectiveRate(LocalDate.of(2026, 5, 31))).isEqualTo(BigDecimal("0.0800"))
        assertThat(effectiveRate(LocalDate.of(2026, 6, 1))).isEqualTo(BigDecimal("0.0700"))
    }

    @Test
    fun `셀러 전용 정책이 전역 정책을 덮는다`() {
        tx.executeWithoutResult {
            em.persist(policy(null, LocalDate.of(2026, 1, 1), "0.0800"))
            em.persist(policy(SELLER_ID, LocalDate.of(2026, 1, 1), "0.0500"))
        }

        assertThat(effectiveRate(LocalDate.of(2026, 3, 10))).isEqualTo(BigDecimal("0.0500"))
        // 다른 셀러는 전역 정책을 그대로 받는다.
        val other = tx.execute { feePolicyRepository.findEffective(SELLER_ID + 1, LocalDate.of(2026, 3, 10)) }
        assertThat(other!!.platformFeeRate).isEqualTo(BigDecimal("0.0800"))
    }

    @Test
    fun `적용 시작일이 아직 오지 않은 정책만 있으면 적용할 정책이 없다`() {
        tx.executeWithoutResult { em.persist(policy(null, LocalDate.of(2026, 6, 1), "0.0700")) }

        val found = tx.execute { feePolicyRepository.findEffective(SELLER_ID, LocalDate.of(2026, 5, 31)) }

        assertThat(found).isNull()
    }

    private fun effectiveRate(on: LocalDate): BigDecimal = tx.execute { feePolicyRepository.findEffective(SELLER_ID, on) }!!.platformFeeRate

    private fun policy(
        sellerId: Long?,
        effectiveFrom: LocalDate,
        platformFeeRate: String,
    ): SettlementFeePolicy = SettlementFeePolicy.of(sellerId, effectiveFrom, BigDecimal(platformFeeRate), BigDecimal("0.0280"))

    private fun settlement(idempotencyKey: String): Settlement =
        Settlement.create(
            sellerId = SELLER_ID,
            orderId = 2L,
            orderItemId = 3L,
            grossAmount = 100_000L,
            shippingFee = 3_000L,
            commission = 8_000L,
            pgFee = 2_800L,
            sellerCouponBurden = 0L,
            platformFeeRate = BigDecimal("0.0800"),
            pgFeeRate = BigDecimal("0.0280"),
            confirmedOn = LocalDate.of(2026, 3, 10),
            idempotencyKey = idempotencyKey,
        )

    private companion object {
        private const val SELLER_ID = 7001L
    }
}
