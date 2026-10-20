package com.aechak.batch.settlement

import com.aechak.application.settlement.usecase.SettlementPayoutUseCase
import com.aechak.application.settlement.usecase.command.ExecuteSettlementPayoutCommand
import com.aechak.domain.settlement.settlement.policy.SettlementCalendar
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDate

/**
 * 정산 집행 스케줄. 매일 새벽 KST 오늘을 기준일로 돌려, 지급 예정일이 도래한 정산을 셀러별 지급 건으로 묶는다.
 * 같은 기준일을 다시 돌려도 지급 건이 늘지 않으므로 재기동에 안전하다.
 *
 * 셀러에게 돈이 나가는 경로의 첫 단계라 기본값은 꺼져 있다 — 환불 집행·자동승인과 같은 규약이다.
 * 지급 건이 한 번 만들어지면 그 정산은 다른 회차가 다시 집지 않으므로 되돌리기가 쉽지 않다.
 */
@Component
@ConditionalOnProperty(name = ["settlement.execution.enabled"], havingValue = "true")
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
