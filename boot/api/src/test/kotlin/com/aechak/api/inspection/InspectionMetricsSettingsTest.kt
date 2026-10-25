package com.aechak.api.inspection

import com.aechak.application.inspection.usecase.InspectionTriageCostModel
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [단위] 손익분기 승급률이 실행 모듈 사이에서 갈라지지 않는지 고정한다.
 *
 * 이 값이 api 설정과 어드민 설정 둘에서 읽히고, 한쪽만 고치면 화면과 주기 로그가 다른 기준으로
 * 경보한다. 실제로 그 상태였다 — 0.457·0.620·0.88 세 숫자가 문서와 코드에 흩어져 있었고, 낮은
 * 값을 쓰면 승급률 45.7~62.0% 구간에서 "손익분기 초과, 선별을 끄라"는 거짓 경보가 난다. 아직
 * 이득인 구간인데도 기능을 끄라고 말하는 셈이다.
 *
 * 레포 안의 사본은 하나다(`InspectionTriageCostModel`). 그 정본은 AI 레포의 측정 하네스이고 두
 * 레포를 묶는 CI 잡이 없으므로 **이 테스트는 하네스와의 일치를 보증하지 않는다** — 보는 것은 이
 * 모듈 설정이 레포 사본과 같은지다. 하네스 값이 바뀌면 사본을 손으로 고치고, 고치는 자리가 하나라
 * 두 모듈이 어긋날 자리가 없다.
 */
class InspectionMetricsSettingsTest {
    @Test
    fun `손익분기 승급률이 레포 사본과 같다`() {
        val matched =
            BREAKEVEN_LINE.find(readResource("application.yml"))
                ?: error("application.yml 에 promotion-breakeven-rate 설정이 없다")

        assertEquals(
            InspectionTriageCostModel.BREAKEVEN_PROMOTION_RATE,
            matched.groupValues[1].toDouble(),
            "손익분기 승급률이 레포 사본과 다르다. 한쪽만 고치면 화면과 주기 로그가 다른 기준으로 경보한다",
        )
    }

    /** 임계·창·주기 로그 설정이 빠지지 않는지 본다. 빠지면 기본값으로 떨어져 모듈 간에 동작이 갈린다 */
    @Test
    fun `지표 설정이 빠지지 않는다`() {
        val keys =
            listOf(
                "window-hours",
                "backlog-warn-count",
                "backlog-warn-age-minutes",
                "max-window-days",
                "snapshot-cache-seconds",
                "log-interval-ms",
            )
        val text = readResource("application.yml")

        keys.forEach { key ->
            assertTrue(text.contains("$key:"), "application.yml 에 inspection.metrics.$key 가 없다")
        }
    }

    /**
     * 지표 로그가 기한 정리 스윕을 막지 않게 스케줄러 풀이 둘 이상이다.
     *
     * 스프링 기본값은 1이다. 그 한 스레드에 스냅샷 한 장(쿼리 열넷 + 적체 집계)과 미판정 검수를
     * 닫아 셀러를 풀어 주는 스윕이 함께 올라가면, 적체가 쌓여 스냅샷이 느려진 동안 그 안전장치가
     * 한 번도 돌지 못한다 — 느려지는 조건과 스윕이 필요한 조건이 같은 상태다.
     */
    @Test
    fun `스케줄러 풀이 지표 로그와 기한 정리를 함께 담는다`() {
        val matched =
            SCHEDULER_POOL_LINE.find(readResource("application.yml"))
                ?: error("application.yml 에 spring.task.scheduling.pool.size 설정이 없다")

        assertTrue(
            matched.groupValues[1].toInt() >= 2,
            "스케줄러 풀이 1이면 지표 스냅샷이 기한 정리 스윕을 막는다",
        )
    }

    private fun readResource(name: String): String =
        requireNotNull(javaClass.getResourceAsStream("/$name")) { "설정 리소스를 찾지 못했다: $name" }
            .readAllBytes()
            .decodeToString()

    companion object {
        private val BREAKEVEN_LINE = Regex("""promotion-breakeven-rate:\s*([0-9.]+)""")

        /** `scheduling:` 블록에서 처음 나오는 `size:` 가 풀 크기다 */
        private val SCHEDULER_POOL_LINE = Regex("""scheduling:[\s\S]*?size:\s*(\d+)""")
    }
}
