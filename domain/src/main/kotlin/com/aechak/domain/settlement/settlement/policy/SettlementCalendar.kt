package com.aechak.domain.settlement.settlement.policy

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 정산 달력은 KST 기준이다. 정산월과 지급 예정일이 전부 이 기준일에서 파생된다.
 * 구매확정 시각은 서버 벽시계로 기록되므로 그 시각이 적힌 시간대를 함께 받아 KST로 옮긴다 —
 * 서버 시간대가 KST가 아닌 환경에서 자정 전후 확정이 다른 달로 새는 것을 막는다.
 */
object SettlementCalendar {
    val ZONE: ZoneId = ZoneId.of("Asia/Seoul")

    fun settlementDateOf(
        confirmedAt: LocalDateTime,
        writtenIn: ZoneId = ZoneId.systemDefault(),
    ): LocalDate = confirmedAt.atZone(writtenIn).withZoneSameInstant(ZONE).toLocalDate()
}
