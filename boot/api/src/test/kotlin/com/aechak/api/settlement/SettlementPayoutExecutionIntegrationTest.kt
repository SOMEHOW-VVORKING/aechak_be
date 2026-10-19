package com.aechak.api.settlement

import com.aechak.api.support.IntegrationTestBase
import com.aechak.application.settlement.usecase.SettlementPayoutUseCase
import com.aechak.application.settlement.usecase.command.ExecuteSettlementPayoutCommand
import com.aechak.application.settlement.usecase.result.PayoutExclusionReason
import com.aechak.application.settlement.usecase.result.SettlementPayoutExecutionResult
import com.aechak.application.settlement.usecase.result.SettlementPayoutOutcome
import com.aechak.domain.settlement.account.SettlementAccount
import com.aechak.domain.settlement.account.enums.AccountVerificationStatus
import com.aechak.domain.settlement.batch.SettlementExecutionBatch
import com.aechak.domain.settlement.batch.enums.SettlementBatchStatus
import com.aechak.domain.settlement.payout.Payout
import com.aechak.domain.settlement.payout.enums.PayoutStatus
import com.aechak.domain.settlement.settlement.Settlement
import com.aechak.domain.settlement.settlement.enums.SettlementStatus
import com.aechak.domain.settlement.settlement.repository.SettlementRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.math.BigDecimal
import java.time.LocalDate

/**
 * 정산 집행 배치의 대상 선정·지급 건 생성·멱등 통합 검증(실 MySQL).
 * 깨지면 같은 기준일에 지급이 두 번 생기거나, 보류·환불 차감·미검증 계좌 셀러에게 돈이 나간다.
 */
class SettlementPayoutExecutionIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var useCase: SettlementPayoutUseCase

    @Autowired
    private lateinit var settlementRepository: SettlementRepository

    @Test
    fun `지급 예정일이 기준일까지 도래한 정산만 집행 대상이다`() {
        seedVerifiedAccount(SELLER_A)
        val due = seedSettlement(SELLER_A, orderItemId = 1L, confirmedOn = BASE_DATE.minusDays(5))
        val notDue = seedSettlement(SELLER_A, orderItemId = 2L, confirmedOn = BASE_DATE.minusDays(4))

        val result = execute(BASE_DATE)

        assertThat(result.outcome).isEqualTo(SettlementPayoutOutcome.EXECUTED)
        assertThat(result.createdPayoutCount).isEqualTo(1)
        assertThat(result.createdPayoutAmount).isEqualTo(NET_AMOUNT)
        assertThat(payoutIdOf(due)).isNotNull()
        assertThat(payoutIdOf(notDue)).isNull()
        assertThat(batchOf(BASE_DATE)!!.totalTargetCount).isEqualTo(1)
        assertThat(batchOf(BASE_DATE)!!.totalAmount).isEqualTo(NET_AMOUNT)
    }

    @Test
    fun `지급 건은 셀러별로 하나씩이고 금액은 지급 예정액의 합이다`() {
        seedVerifiedAccount(SELLER_A)
        seedVerifiedAccount(SELLER_B)
        seedSettlement(SELLER_A, orderItemId = 1L)
        seedSettlement(SELLER_A, orderItemId = 2L)
        seedSettlement(SELLER_B, orderItemId = 3L)

        val result = execute(BASE_DATE)

        assertThat(result.createdPayoutCount).isEqualTo(2)
        assertThat(result.createdPayoutAmount).isEqualTo(NET_AMOUNT * 3)
        assertThat(amountOf(SELLER_A)).isEqualTo(NET_AMOUNT * 2)
        assertThat(amountOf(SELLER_B)).isEqualTo(NET_AMOUNT)
        assertThat(payoutCount()).isEqualTo(2)
    }

    @Test
    fun `같은 기준일로 두 번 돌려도 지급 건은 한 번만 생긴다`() {
        seedVerifiedAccount(SELLER_A)
        seedSettlement(SELLER_A, orderItemId = 1L)
        val first = execute(BASE_DATE)

        val second = execute(BASE_DATE)

        assertThat(first.outcome).isEqualTo(SettlementPayoutOutcome.EXECUTED)
        assertThat(second.outcome).isEqualTo(SettlementPayoutOutcome.RESUMED)
        assertThat(second.createdPayoutCount).isEqualTo(0)
        assertThat(payoutCount()).isEqualTo(1)
        assertThat(batchCount()).isEqualTo(1)
    }

    @Test
    fun `다음 기준일 회차는 이미 지급 묶음에 들어간 정산을 다시 집지 않는다`() {
        seedVerifiedAccount(SELLER_A)
        seedSettlement(SELLER_A, orderItemId = 1L)
        execute(BASE_DATE)

        val next = execute(BASE_DATE.plusDays(1))

        assertThat(next.outcome).isEqualTo(SettlementPayoutOutcome.EXECUTED)
        assertThat(next.createdPayoutCount).isEqualTo(0)
        assertThat(payoutCount()).isEqualTo(1)
        assertThat(batchOf(BASE_DATE.plusDays(1))!!.status).isEqualTo(SettlementBatchStatus.COMPLETED)
    }

    @Test
    fun `보류 상태와 환불 차감이 생긴 정산은 집행에서 뺀다`() {
        seedVerifiedAccount(SELLER_A)
        val held = seedSettlement(SELLER_A, orderItemId = 1L).also { mutate(it) { s -> s.hold() } }
        val deducted = seedSettlement(SELLER_A, orderItemId = 2L).also { mutate(it) { s -> s.applyRefundDeduction(1_000L) } }
        val normal = seedSettlement(SELLER_A, orderItemId = 3L)

        val result = execute(BASE_DATE)

        assertThat(result.createdPayoutCount).isEqualTo(1)
        assertThat(amountOf(SELLER_A)).isEqualTo(NET_AMOUNT)
        assertThat(payoutIdOf(normal)).isNotNull()
        assertThat(payoutIdOf(held)).isNull()
        assertThat(payoutIdOf(deducted)).isNull()
        assertThat(statusOf(held)).isEqualTo(SettlementStatus.ON_HOLD)
        assertThat(statusOf(deducted)).isEqualTo(SettlementStatus.PENDING)
    }

    @Test
    fun `정산계좌가 없거나 검증되지 않은 셀러는 집행에서 뺀다`() {
        seedVerifiedAccount(SELLER_A)
        seedAccount(SELLER_B, AccountVerificationStatus.VERIFYING)
        seedSettlement(SELLER_A, orderItemId = 1L)
        val unverified = seedSettlement(SELLER_B, orderItemId = 2L)
        val noAccount = seedSettlement(SELLER_C, orderItemId = 3L)

        val result = execute(BASE_DATE)

        assertThat(result.createdPayoutCount).isEqualTo(1)
        assertThat(result.excludedSellers.map { it.sellerId to it.reason })
            .containsExactlyInAnyOrder(
                SELLER_B to PayoutExclusionReason.ACCOUNT_NOT_VERIFIED,
                SELLER_C to PayoutExclusionReason.ACCOUNT_NOT_FOUND,
            )
        assertThat(payoutIdOf(unverified)).isNull()
        assertThat(payoutIdOf(noAccount)).isNull()
        // 제외된 셀러의 정산은 계좌가 검증되면 다음 회차가 그대로 집는다
        assertThat(statusOf(unverified)).isEqualTo(SettlementStatus.PENDING)
        assertThat(batchOf(BASE_DATE)!!.totalTargetCount).isEqualTo(1)
    }

    @Test
    fun `집행 대상이 없으면 배치만 남고 지급 건은 생기지 않는다`() {
        val result = execute(BASE_DATE)

        assertThat(result.outcome).isEqualTo(SettlementPayoutOutcome.EXECUTED)
        assertThat(result.createdPayoutCount).isEqualTo(0)
        assertThat(payoutCount()).isEqualTo(0)
        assertThat(batchOf(BASE_DATE)!!.status).isEqualTo(SettlementBatchStatus.COMPLETED)
        assertThat(batchOf(BASE_DATE)!!.totalTargetCount).isEqualTo(0)
    }

    @Test
    fun `지급 건은 송금 요청 상태로 남고 묶인 정산은 입금 확인 전이라 정산대기 그대로다`() {
        seedVerifiedAccount(SELLER_A)
        val settlement = seedSettlement(SELLER_A, orderItemId = 1L)

        execute(BASE_DATE)

        val payout = payoutOf(SELLER_A)!!
        assertThat(payout.status).isEqualTo(PayoutStatus.SENT)
        // 지급대행 API를 부르지 않는다 — 식별자가 비어 있는 것이 수기 이체 대상이라는 표식이다
        assertThat(payout.portonePayoutId).isNull()
        assertThat(payout.processedAt).isNotNull()
        assertThat(payout.transferKey).isEqualTo(Payout.transferKeyOf(batchOf(BASE_DATE)!!.id, SELLER_A))
        assertThat(statusOf(settlement)).isEqualTo(SettlementStatus.PENDING)
        assertThat(payoutIdOf(settlement)).isEqualTo(payout.id)
        assertThat(batchOf(BASE_DATE)!!.status).isEqualTo(SettlementBatchStatus.PROCESSING)
    }

    private fun execute(baseDate: LocalDate): SettlementPayoutExecutionResult =
        useCase.executePayout(ExecuteSettlementPayoutCommand(baseDate))

    private fun seedSettlement(
        sellerId: Long,
        orderItemId: Long,
        confirmedOn: LocalDate = BASE_DATE.minusDays(Settlement.PAYOUT_DELAY_DAYS),
    ): Long =
        tx.execute {
            settlementRepository
                .saveAll(
                    listOf(
                        Settlement.create(
                            sellerId = sellerId,
                            orderId = orderItemId,
                            orderItemId = orderItemId,
                            grossAmount = 100_000L,
                            shippingFee = 3_000L,
                            commission = 8_000L,
                            pgFee = 2_800L,
                            sellerCouponBurden = 0L,
                            platformFeeRate = BigDecimal("0.0800"),
                            pgFeeRate = BigDecimal("0.0280"),
                            confirmedOn = confirmedOn,
                            idempotencyKey = Settlement.idempotencyKeyOf(orderItemId),
                        ),
                    ),
                ).first()
                .id
        }!!

    /** 심어 둔 원장의 상태를 도메인 메서드로 바꾼다 — 테스트가 컬럼을 직접 쓰면 전이 규칙을 비껴간다. */
    private fun mutate(
        settlementId: Long,
        change: (Settlement) -> Unit,
    ) = tx.executeWithoutResult {
        change(em.find(Settlement::class.java, settlementId))
        em.flush()
    }

    private fun seedVerifiedAccount(sellerId: Long) = seedAccount(sellerId, AccountVerificationStatus.VERIFIED)

    private fun seedAccount(
        sellerId: Long,
        verificationStatus: AccountVerificationStatus,
    ) = tx.executeWithoutResult {
        em.persist(SettlementAccount.registerVerified(sellerId, "088", "enc", "홍길동"))
        em.flush()
        em
            .createQuery("update SettlementAccount a set a.verificationStatus = :st where a.sellerId = :sellerId")
            .setParameter("st", verificationStatus)
            .setParameter("sellerId", sellerId)
            .executeUpdate()
    }

    private fun statusOf(settlementId: Long): SettlementStatus = reload(settlementId).status

    private fun payoutIdOf(settlementId: Long): Long? = tx.execute { reload(settlementId).payout?.id }

    private fun reload(settlementId: Long): Settlement = tx.execute { em.find(Settlement::class.java, settlementId) }!!

    private fun payoutOf(sellerId: Long): Payout? =
        tx.execute {
            em
                .createQuery("select p from Payout p where p.sellerId = :sellerId", Payout::class.java)
                .setParameter("sellerId", sellerId)
                .resultList
                .firstOrNull()
        }

    private fun amountOf(sellerId: Long): Long = payoutOf(sellerId)!!.settlementTargetAmount

    private fun payoutCount(): Long =
        tx.execute { em.createQuery("select count(p) from Payout p", Long::class.javaObjectType).singleResult }!!

    private fun batchCount(): Long =
        tx.execute { em.createQuery("select count(b) from SettlementExecutionBatch b", Long::class.javaObjectType).singleResult }!!

    private fun batchOf(baseDate: LocalDate): SettlementExecutionBatch? =
        tx.execute {
            em
                .createQuery(
                    "select b from SettlementExecutionBatch b where b.settlementBaseDate = :baseDate",
                    SettlementExecutionBatch::class.java,
                ).setParameter("baseDate", baseDate)
                .resultList
                .firstOrNull()
        }

    private companion object {
        private val BASE_DATE = LocalDate.of(2026, 3, 20)
        private const val SELLER_A = 7001L
        private const val SELLER_B = 7002L
        private const val SELLER_C = 7003L

        /** 100,000 + 3,000 − 8,000 − 2,800 */
        private const val NET_AMOUNT = 92_200L
    }
}
