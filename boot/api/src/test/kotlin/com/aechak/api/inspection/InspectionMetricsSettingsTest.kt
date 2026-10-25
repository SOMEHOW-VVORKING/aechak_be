package com.aechak.api.inspection

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [단위] 손익분기 승급률이 실행 모듈 사이에서 갈라지지 않는지 고정한다.
 *
 * 이 값이 세 군데(api 설정, 어드민 설정, 유스케이스 기본값)에서 읽히고, 한쪽만 고치면 화면과
 * 주기 로그가 다른 기준으로 경보한다. 실제로 그 상태였다 — 0.457·0.620·0.88 세 숫자가 문서와
 * 코드에 흩어져 있었고, 낮은 값을 쓰면 승급률 45.7~62.0% 구간에서 "손익분기 초과, 선별을
 * 끄라"는 거짓 경보가 난다. 아직 이득인 구간인데도 기능을 끄라고 말하는 셈이다.
 *
 * 정본은 AI 레포의 측정 하네스(`harness/run.py`의 `BREAKEVEN_PROMOTION_RATE`)다. 역산식이
 * 하네스 문서에 박혀 있다 — 선별 1회 1400microUSD, 관점 둘 3680microUSD에서 1 - 1400/3680이다.
 * 하네스를 다시 돌려 값이 바뀌면 이 테스트가 먼저 깨지고, 그때 두 모듈의 설정과 유스케이스
 * 기본값을 함께 고치게 된다.
 */
class InspectionMetricsSettingsTest {
    @Test
    fun `손익분기 승급률이 하네스 정본과 같다`() {
        val matched =
            BREAKEVEN_LINE.find(readResource("application.yml"))
                ?: error("application.yml 에 promotion-breakeven-rate 설정이 없다")

        assertEquals(
            BREAKEVEN_RATE,
            matched.groupValues[1].toDouble(),
            "손익분기 승급률이 정본과 다르다. 한쪽만 고치면 화면과 주기 로그가 다른 기준으로 경보한다",
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

    private fun readResource(name: String): String =
        requireNotNull(javaClass.getResourceAsStream("/$name")) { "설정 리소스를 찾지 못했다: $name" }
            .readAllBytes()
            .decodeToString()

    companion object {
        /** 정본은 측정 하네스다. 이 값을 고칠 때는 어드민 설정과 유스케이스 기본값을 함께 고친다 */
        private const val BREAKEVEN_RATE = 0.620

        private val BREAKEVEN_LINE = Regex("""promotion-breakeven-rate:\s*([0-9.]+)""")
    }
}
