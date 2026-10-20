package com.aechak.api.config

import com.aechak.application.inspection.usecase.InspectionUseCase
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 판정을 못 받은 검수를 기한 뒤에 닫는다.
 *
 * AI 서버가 죽거나 브로커가 끊겨도 셀러가 영구히 막히지 않게 하는 안전장치다.
 * 닫을 때 통과로 적지 않고 미판정으로 적는다 — 통과로 적으면 지표에서 "검수를 통과한 상품"과
 * "아무도 보지 않은 상품"이 섞여 구분할 수 없게 된다.
 *
 * 여러 인스턴스가 동시에 돌 수 있다. 같은 행을 두 번 닫아도 두 번째가 조회에서 이미 빠지고,
 * 엔티티의 낙관적 락이 동시 수정을 심판한다.
 */
@ConditionalOnProperty("spring.kafka.bootstrap-servers")
@EnableScheduling
@Component
class InspectionExpirySchedule(
    private val inspectionUseCase: InspectionUseCase,
) {
    @Scheduled(fixedDelay = SWEEP_INTERVAL_MS)
    fun poll() {
        inspectionUseCase.expireOverdue()
    }

    companion object {
        private const val SWEEP_INTERVAL_MS = 60_000L
    }
}
