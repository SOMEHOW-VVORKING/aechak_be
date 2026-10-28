package com.aechak.application.inspection.usecase.result

import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [단위] "모델이 읽지 않은 통과" 경보의 임계 판정을 고정한다.
 *
 * 건수와 비율을 **둘 다** 넘어야 경보한다. 한쪽만 보면 양쪽 방향으로 오경보가 난다 — 건수만
 * 보면 트래픽이 늘 때 정상 비율에서도 울고, 비율만 보면 창에 두 건 있고 한 건이 걸릴 때 50%로
 * 운다. 통합 테스트로는 비율 축을 가르려고 수백 건을 심어야 해서 여기서 직접 본다.
 */
class InspectionMetricsCoverageAlarmTest {
    @Test
    fun `건수와 비율을 둘 다 넘으면 경보한다`() {
        assertTrue(coverage(settled = 100, modelUnverified = 5).modelUnverifiedBreached())
    }

    @Test
    fun `비율은 넘었지만 건수가 모자라면 경보하지 않는다`() {
        // 창에 두 건뿐이고 한 건이 걸렸다. 50%지만 운영이 손댈 신호가 아니다.
        assertFalse(coverage(settled = 2, modelUnverified = 1).modelUnverifiedBreached())
    }

    @Test
    fun `건수는 넘었지만 비율이 모자라면 경보하지 않는다`() {
        // 트래픽이 커지면 같은 건수가 정상 범위가 된다. 건수만 보면 그때부터 상시 경보다.
        assertFalse(coverage(settled = 1_000, modelUnverified = 5).modelUnverifiedBreached())
    }

    @Test
    fun `창에 닫힌 검수가 없으면 경보하지 않는다`() {
        // 분자는 분모의 부분집합이라 함께 0이다. 비율 계산이 0으로 나누는 자리이기도 하다.
        assertFalse(coverage(settled = 0, modelUnverified = 0).modelUnverifiedBreached())
    }

    @Test
    fun `경보 문장에 싣는 비율은 분모가 0이어도 터지지 않는다`() {
        assertTrue(coverage(settled = 0, modelUnverified = 0).modelUnverifiedPercent() == 0.0)
    }

    private fun coverage(
        settled: Long,
        modelUnverified: Long,
    ) = InspectionMetricsResult.Coverage(
        settled,
        modelUnverified,
        0,
        0,
        modelUnverified,
        0,
        null,
        WARN_COUNT,
        WARN_PERCENT,
    )

    private companion object {
        /** `application.yml`의 `inspection.metrics.model-unverified-warn-*`과 같은 값이어야 뜻이 있다 */
        const val WARN_COUNT = 3L
        const val WARN_PERCENT = 1L
    }
}
