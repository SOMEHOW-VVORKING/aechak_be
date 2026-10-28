package com.aechak.batch.payment

import com.aechak.application.payment.port.PaymentReconciliationSourcePort
import com.aechak.application.payment.reconciliation.usecase.PaymentReconciliationUseCase
import com.aechak.application.payment.reconciliation.usecase.command.CatchUpReconciliationCommand
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.ZoneId

/**
 * 결제 대사 스케줄. 매일 새벽 전날 기준일로 돌리되, 마지막 완료일 이후 빠진 날이 있으면 순서대로 메운다.
 * 조립된 소스(포트원, 켜져 있으면 토스)마다 따로 돈다. 같은 날을 다시 돌려도 완료된 회차는 건너뛰므로 재기동에 안전하다.
 */
@Component
class PaymentReconciliationSchedule(
    private val useCase: PaymentReconciliationUseCase,
    private val sources: List<PaymentReconciliationSourcePort>,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "\${payment.reconciliation.cron:0 30 4 * * *}", zone = "Asia/Seoul")
    fun runDaily() {
        val yesterday = LocalDate.now(KST).minusDays(1)
        sources.forEach { source ->
            try {
                val results = useCase.catchUp(CatchUpReconciliationCommand(source.source, yesterday))
                results.forEach {
                    log.info("결제 대사 결과. source={}, baseDate={}, outcome={}, runId={}", it.source, it.baseDate, it.outcome, it.runId)
                }
            } catch (e: Exception) {
                // 소스 하나의 장애가 다른 소스의 대사를 막지 않게 한다
                log.error("결제 대사 스케줄 실패. source={}", source.source, e)
            }
        }
    }

    companion object {
        private val KST = ZoneId.of("Asia/Seoul")
    }
}
