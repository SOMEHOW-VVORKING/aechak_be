package com.aechak.application.payment.reconciliation.usecase.command

import com.aechak.domain.payment.reconciliation.ReconciliationSource
import java.time.LocalDate

/**
 * 마지막으로 완료된 기준일 다음 날부터 untilBaseDate까지 빠진 날을 순서대로 돌린다.
 * 서버가 며칠 꺼져 있다 올라와도 어제 하루만 보고 끝내지 않기 위한 입구. 상한을 두어 폭주를 막는다.
 */
data class CatchUpReconciliationCommand(
    val source: ReconciliationSource,
    val untilBaseDate: LocalDate,
    val maxDays: Int = DEFAULT_MAX_DAYS,
) {
    init {
        require(maxDays in 1..MAX_DAYS_LIMIT) { "maxDays는 1..$MAX_DAYS_LIMIT 이어야 합니다: $maxDays" }
    }

    companion object {
        const val DEFAULT_MAX_DAYS = 7
        const val MAX_DAYS_LIMIT = 31
    }
}
