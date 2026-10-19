package com.aechak.application.settlement.service

import com.aechak.application.settlement.usecase.result.ExcludedPayoutSeller
import com.aechak.application.settlement.usecase.result.PayoutExclusionReason
import com.aechak.common.error.BusinessException
import com.aechak.domain.settlement.account.enums.AccountVerificationStatus
import com.aechak.domain.settlement.account.repository.SettlementAccountRepository
import com.aechak.domain.settlement.batch.SettlementExecutionBatch
import com.aechak.domain.settlement.batch.enums.SettlementBatchStatus
import com.aechak.domain.settlement.batch.repository.SettlementExecutionBatchRepository
import com.aechak.domain.settlement.error.SettlementErrorCode
import com.aechak.domain.settlement.payout.Payout
import com.aechak.domain.settlement.payout.repository.PayoutRepository
import com.aechak.domain.settlement.settlement.Settlement
import com.aechak.domain.settlement.settlement.repository.SettlementRepository
import org.springframework.stereotype.Service
import java.time.LocalDate

/** 정산 집행 로직 보관함 — SettlementPayoutFacade에서만 호출된다. 각 메서드는 호출자가 연 트랜잭션 안에서 돈다. */
@Service
class SettlementPayoutService(
    private val settlementRepository: SettlementRepository,
    private val settlementAccountRepository: SettlementAccountRepository,
    private val payoutRepository: PayoutRepository,
    private val settlementExecutionBatchRepository: SettlementExecutionBatchRepository,
) {
    /**
     * 기준일 배치를 편성하고 지급할 셀러를 가려낸다. 같은 기준일 배치가 이미 있으면 새로 만들지 않고 그 배치를 이어받는다 —
     * 기준일 UNIQUE가 두 번째 편성을 막으므로 배치 1행이 "이 기준일은 한 번만 집행한다"의 근거다.
     *
     * 배치에 적히는 건수·금액은 **편성 시점의 집계**다. 이어받는 회차는 남은 정산만 처리하고 집계는 고치지 않는다.
     */
    fun startOrResume(baseDate: LocalDate): PayoutPlan {
        val existing = settlementExecutionBatchRepository.findBySettlementBaseDate(baseDate)
        val bySeller =
            settlementRepository
                .findAllPayoutCandidates(baseDate)
                .filter { it.isPayoutEligible() }
                .groupBy { it.sellerId }
        val accounts =
            settlementAccountRepository
                .findAllBySellerIdIn(bySeller.keys.sorted())
                .associateBy { it.sellerId }

        val payableSellerIds = mutableListOf<Long>()
        val excludedSellers = mutableListOf<ExcludedPayoutSeller>()
        bySeller.keys.sorted().forEach { sellerId ->
            val reason = exclusionReasonOf(accounts[sellerId]?.verificationStatus, bySeller.getValue(sellerId))
            if (reason == null) payableSellerIds += sellerId else excludedSellers += ExcludedPayoutSeller(sellerId, reason)
        }

        val payableSettlements = payableSellerIds.flatMap { bySeller.getValue(it) }
        val batch =
            existing ?: settlementExecutionBatchRepository.save(
                SettlementExecutionBatch.generate(
                    settlementBaseDate = baseDate,
                    totalTargetCount = payableSettlements.size,
                    totalAmount = payableSettlements.sumOf { it.netAmount },
                ),
            )
        return PayoutPlan(
            batchId = batch.id,
            resumed = existing != null,
            payableSellerIds = payableSellerIds,
            excludedSellers = excludedSellers,
        )
    }

    /**
     * 셀러 하나의 지급 건을 만들고 그 셀러의 정산을 묶는다. 대상 정산은 이 트랜잭션에서 다시 읽는다 —
     * 편성 때 읽은 행을 들고 오면 영속성 컨텍스트 밖의 객체를 고치게 되고, 그 사이 바뀐 상태도 못 본다.
     *
     * 이미 같은 송금키의 지급 건이 있거나 종결된 회차면(이어받는 회차) 아무것도 하지 않는다.
     * 금액은 선점에 성공한 정산의 지급 예정액 합이고, 묶인 정산은 PENDING 그대로 남는다 —
     * 실제 입금 확인이 PAID를 찍는다.
     *
     * 연결은 조건부 원자 UPDATE로 선점한다. 기준일이 다른 두 회차가 겹쳐 돌면 배치가 달라 송금키 UNIQUE가
     * 막지 못하므로, 선점에 실패한 원장이 하나라도 있으면 이 트랜잭션을 되돌려 지급 건까지 없앤다 —
     * 같은 정산이 금액만큼 두 번 송금 요청되는 것을 막는 유일한 방어선이다.
     */
    fun createPayout(
        baseDate: LocalDate,
        sellerId: Long,
    ): Payout? {
        val batch =
            settlementExecutionBatchRepository.findBySettlementBaseDate(baseDate)
                ?: throw IllegalStateException("기준일 $baseDate 집행 배치가 없다")
        val transferKey = Payout.transferKeyOf(batch.id, sellerId)
        if (!batch.acceptsPayout() || payoutRepository.findByTransferKey(transferKey) != null) {
            return null
        }

        val settlements = settlementRepository.findAllPayoutCandidatesBySellerId(sellerId, baseDate).filter { it.isPayoutEligible() }
        val amount = settlements.sumOf { it.netAmount }
        if (settlements.isEmpty() || amount <= 0L) {
            return null
        }

        // 송금 요청서 발행까지가 v1 범위다 — 지급대행 API를 부르지 않으므로 지급사 식별자는 비워 둔다.
        val payout = payoutRepository.save(Payout.create(batch, sellerId, amount, transferKey).also { it.markSent(null) })
        if (settlementRepository.assignPayoutIfEligible(payout, settlements.map { it.id }) != settlements.size) {
            throw BusinessException(SettlementErrorCode.PAYOUT_TARGET_TAKEN)
        }
        return payout
    }

    /**
     * 회차를 닫는다. 편성 상태의 배치만 움직이므로 이어받는 회차가 다시 전이시키지 않는다.
     * 종결은 더 처리할 것이 남지 않은 회차만 — 지급 건도, 실패도, 제외된 셀러도 없을 때다.
     * 남은 것이 있는데 종결하면 같은 기준일을 다시 돌릴 때 종결된 회차에 송금 요청이 붙는다.
     */
    fun finish(
        baseDate: LocalDate,
        pendingWork: Boolean,
    ): SettlementBatchStatus? {
        val batch = settlementExecutionBatchRepository.findBySettlementBaseDate(baseDate) ?: return null
        if (batch.status == SettlementBatchStatus.GENERATED) {
            batch.startProcessing()
            if (!pendingWork && payoutRepository.countByBatchId(batch.id) == 0L) {
                batch.complete()
            }
        }
        return batch.status
    }

    private fun exclusionReasonOf(
        verificationStatus: AccountVerificationStatus?,
        settlements: List<Settlement>,
    ): PayoutExclusionReason? =
        when {
            verificationStatus == null -> PayoutExclusionReason.ACCOUNT_NOT_FOUND
            verificationStatus != AccountVerificationStatus.VERIFIED -> PayoutExclusionReason.ACCOUNT_NOT_VERIFIED
            settlements.sumOf { it.netAmount } <= 0L -> PayoutExclusionReason.NON_POSITIVE_AMOUNT
            else -> null
        }

    /** 편성 결과 — 셀러 단위 트랜잭션들이 소비한다. 엔티티는 싣지 않는다(트랜잭션을 넘기면 준영속이 된다). */
    data class PayoutPlan(
        val batchId: Long,
        val resumed: Boolean,
        val payableSellerIds: List<Long>,
        val excludedSellers: List<ExcludedPayoutSeller>,
    )
}
