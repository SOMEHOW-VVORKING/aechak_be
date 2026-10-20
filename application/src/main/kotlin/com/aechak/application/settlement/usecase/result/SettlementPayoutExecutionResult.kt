package com.aechak.application.settlement.usecase.result

import com.aechak.domain.settlement.batch.enums.SettlementBatchStatus
import java.time.LocalDate

/**
 * 정산 집행 회차 1건의 결과. 배치 스케줄이 이 값을 로그로 남기는 것이 v1의 유일한 관측 수단이다.
 */
data class SettlementPayoutExecutionResult(
    val baseDate: LocalDate,
    val outcome: SettlementPayoutOutcome,
    val batchStatus: SettlementBatchStatus?,
    /** 이번 회차가 새로 만든 지급 건 수. 재개 회차에서 이미 만들어져 있던 건은 세지 않는다. */
    val createdPayoutCount: Int,
    val createdPayoutAmount: Long,
    /** 지급 대상 정산이 있었지만 집행에서 뺀 셀러들 — 운영자가 계좌를 채워야 한다. */
    val excludedSellers: List<ExcludedPayoutSeller>,
    /** 지급 건 생성이 실패한 셀러 — 다음 회차가 같은 조건으로 다시 집는다. */
    val failedSellerIds: List<Long>,
    val detail: String? = null,
) {
    companion object {
        fun skipped(
            baseDate: LocalDate,
            detail: String,
        ): SettlementPayoutExecutionResult =
            SettlementPayoutExecutionResult(
                baseDate = baseDate,
                outcome = SettlementPayoutOutcome.SKIPPED,
                batchStatus = null,
                createdPayoutCount = 0,
                createdPayoutAmount = 0L,
                excludedSellers = emptyList(),
                failedSellerIds = emptyList(),
                detail = detail,
            )
    }
}

enum class SettlementPayoutOutcome {
    /** 이 기준일을 처음 집행했다. */
    EXECUTED,

    /** 같은 기준일 배치가 이미 있어 아직 지급 묶음에 들어가지 않은 정산만 이어서 처리했다. */
    RESUMED,

    /** 다른 실행이 같은 기준일을 먼저 편성해 손대지 않았다 — 멱등 재호출의 정상 결과. */
    SKIPPED,
}

data class ExcludedPayoutSeller(
    val sellerId: Long,
    val reason: PayoutExclusionReason,
)

enum class PayoutExclusionReason {
    /** 정산계좌가 없다 — 입점 승인 경로가 계좌를 심지 못한 셀러. */
    ACCOUNT_NOT_FOUND,

    /** 계좌 검증이 끝나지 않았다. 검증 전에 송금하면 돌려받을 경로가 없다. */
    ACCOUNT_NOT_VERIFIED,

    /** 지급 예정액 합계가 0 이하다 — 송금할 돈이 없어 지급 건을 만들지 않는다. */
    NON_POSITIVE_AMOUNT,
}
