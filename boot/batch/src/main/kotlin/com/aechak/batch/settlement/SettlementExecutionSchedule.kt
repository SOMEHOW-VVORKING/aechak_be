package com.aechak.batch.settlement

import com.aechak.application.settlement.usecase.SettlementPayoutUseCase
import com.aechak.application.settlement.usecase.command.ExecuteSettlementPayoutCommand
import com.aechak.domain.settlement.settlement.policy.SettlementCalendar
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDate

/**
 * 정산 집행 스케줄. 매일 새벽 KST 오늘을 기준일로 돌려, 지급 예정일이 도래한 정산을 셀러별 지급 건으로 묶는다.
 * 같은 기준일을 다시 돌려도 지급 건이 늘지 않으므로 재기동에 안전하다.
 */
@Component
class SettlementExecutionSchedule(
    private val useCase: SettlementPayoutUseCase,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "\${settlement.execution.cron:0 0 5 * * *}", zone = "Asia/Seoul")
    fun runDaily() {
        val baseDate = LocalDate.now(SettlementCalendar.ZONE)
        try {
            val result = useCase.executePayout(ExecuteSettlementPayoutCommand(baseDate))
            log.info(
                "정산 집행 결과. baseDate={}, outcome={}, batchStatus={}, payouts={}, amount={}, excluded={}, failed={}",
                result.baseDate,
                result.outcome,
                result.batchStatus,
                result.createdPayoutCount,
                result.createdPayoutAmount,
                result.excludedSellers.size,
                result.failedSellerIds.size,
            )
        } catch (e: Exception) {
            log.error("정산 집행 스케줄 실패. baseDate={}", baseDate, e)
        }
    }
}
