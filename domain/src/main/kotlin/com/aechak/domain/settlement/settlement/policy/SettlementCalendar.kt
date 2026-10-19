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

    /**
     * writtenIn 기본값은 변환 시점의 JVM 기본 시간대다 — 기록부터 변환까지 그 시간대가 유지된다는 전제가 선다.
     * 시간대가 바뀐 뒤 같은 주문을 재처리하면 기준일이 달라지므로, 그때는 기록 시간대를 직접 넘겨야 한다.
     */
    fun settlementDateOf(
        confirmedAt: LocalDateTime,
        writtenIn: ZoneId = ZoneId.systemDefault(),
    ): LocalDate = confirmedAt.atZone(writtenIn).withZoneSameInstant(ZONE).toLocalDate()
}
