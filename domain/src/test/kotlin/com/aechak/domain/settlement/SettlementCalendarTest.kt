package com.aechak.domain.settlement

import com.aechak.domain.settlement.settlement.Settlement
import com.aechak.domain.settlement.settlement.policy.SettlementCalendar
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 정산 기준일 계약. 깨지면 자정 전후에 확정된 주문의 정산월이 한 달 어긋나 —
 * 셀러가 그 달 정산에서 통째로 빠지거나 지급 예정일이 하루 밀린다.
 */
class SettlementCalendarTest {
    @Test
    fun `서버가 KST면 확정 시각의 날짜가 그대로 기준일이다`() {
        val confirmedAt = LocalDateTime.of(2026, 11, 1, 0, 30)

        assertEquals(LocalDate.of(2026, 11, 1), SettlementCalendar.settlementDateOf(confirmedAt, SettlementCalendar.ZONE))
    }

    @Test
    fun `서버가 UTC로 기록해도 기준일은 KST 날짜다`() {
        // UTC 벽시계 2026-10-31 15:30 = KST 2026-11-01 00:30
        val confirmedAt = LocalDateTime.of(2026, 10, 31, 15, 30)

        val settlementDate = SettlementCalendar.settlementDateOf(confirmedAt, ZoneId.of("UTC"))

        assertEquals(LocalDate.of(2026, 11, 1), settlementDate)
        assertEquals("2026-11", Settlement.settlementMonthOf(settlementDate))
        assertEquals(LocalDate.of(2026, 11, 6), Settlement.expectedPayoutDateOf(settlementDate))
    }

    @Test
    fun `KST 자정 직전 확정은 그날로 남는다`() {
        val confirmedAt = LocalDateTime.of(2026, 10, 31, 14, 59)

        assertEquals(LocalDate.of(2026, 10, 31), SettlementCalendar.settlementDateOf(confirmedAt, ZoneId.of("UTC")))
    }
}
