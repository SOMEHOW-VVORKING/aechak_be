package com.aechak.application.dashboard.seller

import com.aechak.application.dashboard.seller.usecase.query.DashboardPeriod
import com.aechak.common.error.BusinessException
import com.aechak.common.error.CommonErrorCode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration
import java.time.LocalDateTime

class DashboardPeriodTest {
    private val now = LocalDateTime.of(2026, 10, 19, 13, 30)

    @Test
    fun `전체 기간은 날짜 축을 걸지 않고 직전 기간도 없다`() {
        val window = DashboardPeriod.ALL.windowAt(now)

        assertNull(window.current, "전체 기간에 주문일 하한을 걸면 전체가 아니다")
        assertNull(window.previous, "비교 대상이 없으므로 직전 기간이 있으면 안 된다")
    }

    @Test
    fun `기간은 지금까지의 반개구간이고 직전 기간은 같은 길이로 맞붙는다`() {
        val window = DashboardPeriod.LAST_7_DAYS.windowAt(now)

        assertEquals(now.minusDays(7), window.current!!.from)
        assertEquals(now, window.current!!.toExclusive)
        assertEquals(now.minusDays(14), window.previous!!.from)
        assertEquals(
            window.current!!.from,
            window.previous!!.toExclusive,
            "직전 기간의 상한이 현재 기간의 하한과 어긋나면 경계의 주문이 두 번 세어지거나 빠진다",
        )
    }

    @Test
    fun `기간 코드는 프론트가 보내는 값 그대로다`() {
        assertEquals(DashboardPeriod.ALL, DashboardPeriod.from("all"))
        assertEquals(DashboardPeriod.LAST_7_DAYS, DashboardPeriod.from("7d"))
        assertEquals(DashboardPeriod.LAST_30_DAYS, DashboardPeriod.from("30d"))
        assertEquals(DashboardPeriod.LAST_90_DAYS, DashboardPeriod.from("90d"))
        assertEquals(DashboardPeriod.ALL, DashboardPeriod.DEFAULT, "기간을 주지 않은 로그인 직후 랜딩은 전체 기간이다")
    }

    @Test
    fun `30일과 90일 구간의 길이도 코드와 일치한다`() {
        assertEquals(30L, lengthInDays(DashboardPeriod.LAST_30_DAYS))
        assertEquals(90L, lengthInDays(DashboardPeriod.LAST_90_DAYS))
    }

    @Test
    fun `지원하지 않는 코드는 잘못된 요청이다`() {
        val exception = assertThrows<BusinessException> { DashboardPeriod.from("1d") }

        assertEquals(CommonErrorCode.INVALID_REQUEST, exception.errorCode)
    }

    private fun lengthInDays(period: DashboardPeriod): Long {
        val current = period.windowAt(now).current!!
        return Duration.between(current.from, current.toExclusive).toDays()
    }
}
