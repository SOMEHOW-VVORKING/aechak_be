package com.aechak.application.dashboard.seller.usecase.query

import com.aechak.common.error.BusinessException
import com.aechak.common.error.CommonErrorCode
import java.time.LocalDateTime

/**
 * 대시보드 기간 필터. 직전 기간은 같은 길이의 바로 앞 구간이고, 전체 기간에는 직전 구간이 없다 —
 * 집계 스냅샷 테이블이 없어 증감은 같은 쿼리를 한 번 더 돌려 구한다.
 */
enum class DashboardPeriod(
    val code: String,
    private val days: Long?,
) {
    ALL("all", null),
    LAST_7_DAYS("7d", 7L),
    LAST_30_DAYS("30d", 30L),
    LAST_90_DAYS("90d", 90L),
    ;

    fun windowAt(now: LocalDateTime): DashboardWindow =
        days?.let {
            DashboardWindow(
                at = now,
                current = DashboardWindow.Range(from = now.minusDays(it), toExclusive = now),
                previous = DashboardWindow.Range(from = now.minusDays(it * 2), toExclusive = now.minusDays(it)),
            )
        } ?: DashboardWindow(at = now, current = null, previous = null)

    companion object {
        val DEFAULT = ALL

        fun from(code: String): DashboardPeriod =
            entries.find { it.code == code }
                ?: throw BusinessException(CommonErrorCode.INVALID_REQUEST, detail = "period: 지원하지 않는 값입니다.")
    }
}
