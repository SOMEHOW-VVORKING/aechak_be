package com.aechak.application.settlement.facade

import com.aechak.application.settlement.service.SettlementPayoutService
import com.aechak.application.settlement.usecase.SettlementPayoutUseCase
import com.aechak.application.settlement.usecase.command.ExecuteSettlementPayoutCommand
import com.aechak.application.settlement.usecase.result.SettlementPayoutExecutionResult
import com.aechak.application.settlement.usecase.result.SettlementPayoutOutcome
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

/**
 * SettlementPayoutUseCase의 유일한 구현체. 정산 BC의 두 번째 파사드다 — 배치만 소비하고
 * 원장 생성 파사드와 주입 의존이 겹치지 않는다.
 *
 * 트랜잭션은 단계마다 짧게 연다. 편성(배치 1행)과 셀러별 지급 건 생성을 한 트랜잭션에 묶지 않는 이유는
 * 셀러 한 명의 실패가 나머지 셀러의 지급까지 되돌리지 않게 하기 위함이다. 실패로 남은 정산은
 * 아직 지급 묶음에 들어가지 않았으므로 다음 회차가 같은 조건으로 다시 집는다.
 */
@Service
class SettlementPayoutFacade(
    private val service: SettlementPayoutService,
    transactionManager: PlatformTransactionManager,
) : SettlementPayoutUseCase {
    private val log = LoggerFactory.getLogger(javaClass)
    private val tx = TransactionTemplate(transactionManager)

    override fun executePayout(command: ExecuteSettlementPayoutCommand): SettlementPayoutExecutionResult {
        val baseDate = command.baseDate
        val plan =
            try {
                tx.execute { service.startOrResume(baseDate) }!!
            } catch (e: DataIntegrityViolationException) {
                // 같은 기준일을 두 실행이 동시에 편성했다 — 기준일 UNIQUE가 심판이고 진 쪽은 물러난다
                log.warn("정산 집행 편성 경합 — 다른 실행이 같은 기준일을 먼저 편성. baseDate={}", baseDate, e)
                return SettlementPayoutExecutionResult.skipped(baseDate, "다른 실행이 같은 기준일 배치를 먼저 편성함")
            }
        plan.excludedSellers.forEach {
            log.warn("정산 집행 제외 셀러. baseDate={}, sellerId={}, reason={}", baseDate, it.sellerId, it.reason)
        }

        var createdPayoutCount = 0
        var createdPayoutAmount = 0L
        val failedSellerIds = mutableListOf<Long>()
        plan.payableSellerIds.forEach { sellerId ->
            try {
                val payout = tx.execute { service.createPayout(baseDate, sellerId) }
                if (payout == null) {
                    log.info("정산 지급 건 건너뜀 — 이미 만들어졌거나 남은 대상이 없음. baseDate={}, sellerId={}", baseDate, sellerId)
                } else {
                    createdPayoutCount++
                    createdPayoutAmount += payout.settlementTargetAmount
                }
            } catch (e: Exception) {
                log.error("정산 지급 건 생성 실패 — 다음 회차가 다시 집는다. baseDate={}, sellerId={}", baseDate, sellerId, e)
                failedSellerIds += sellerId
            }
        }

        return SettlementPayoutExecutionResult(
            baseDate = baseDate,
            outcome = if (plan.resumed) SettlementPayoutOutcome.RESUMED else SettlementPayoutOutcome.EXECUTED,
            batchStatus = tx.execute { service.finish(baseDate) },
            createdPayoutCount = createdPayoutCount,
            createdPayoutAmount = createdPayoutAmount,
            excludedSellers = plan.excludedSellers,
            failedSellerIds = failedSellerIds,
        )
    }
}
