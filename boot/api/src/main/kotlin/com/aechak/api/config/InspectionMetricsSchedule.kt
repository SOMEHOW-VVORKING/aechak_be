package com.aechak.api.config

import com.aechak.application.inspection.usecase.InspectionMetricsUseCase
import com.aechak.application.inspection.usecase.result.InspectionMetricsResult
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 검수 운영 지표를 주기적으로 로그에 적는다.
 *
 * 어드민 화면에도 같은 수치가 있지만 그것은 사람이 열어 봐야 보인다. 적체는 아무도 열어 보지
 * 않을 때가 문제인 지표다 — 쌓인 것을 아무도 안 보면 "사람 검수 단계가 있다"는 말이 거짓이 된다.
 * 그래서 임계를 넘으면 사람이 찾아오지 않아도 로그에 경보로 올라간다.
 *
 * 임계 판정과 경보 문장은 유스케이스 결과가 들고 있다. 여기서 다시 쓰면 화면에는 경보가 뜨는데
 * 로그는 조용한 상태, 또는 둘이 다른 문장을 말하는 상태가 생긴다.
 *
 * 4단 뒤집힘율로는 경보하지 않는다. 비평 호출이 원장에 전용 관점으로 적히지 않아 실제로 돌았는지
 * 가를 수 없고, 비평이 꺼져 있으면 분자가 0이 되어 "비평이 쓸모없다"는 반대 결론이 나온다.
 *
 * 첫 실행을 한 주기 뒤로 민다. 부팅 직후에는 창 안에 아무것도 없어 전부 0인 한 줄만 남고,
 * 그 줄이 지표가 0이라는 뜻으로 읽힌다.
 */
@ConditionalOnProperty("inspection.metrics.log-enabled", matchIfMissing = true)
@EnableScheduling
@Component
class InspectionMetricsSchedule(
    private val inspectionMetricsUseCase: InspectionMetricsUseCase,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(
        fixedDelayString = "\${inspection.metrics.log-interval-ms:600000}",
        initialDelayString = "\${inspection.metrics.log-interval-ms:600000}",
    )
    fun report() {
        val snapshot =
            runCatching { inspectionMetricsUseCase.snapshot() }
                .onFailure {
                    // 집계가 깨져도 서비스는 돌아야 한다. 다만 조용히 넘기면 지표가 사라진 것을 모른다.
                    log.error("검수 지표 집계가 실패했다. 지표 없이 운영되는 상태다", it)
                }.getOrNull() ?: return

        val line = format(snapshot)
        if (snapshot.needsAttention()) {
            log.warn("검수 지표 경보 — {} / {}", snapshot.alarmLine(), line)
        } else {
            log.info("검수 지표 {}", line)
        }
    }

    /**
     * 지표를 한 줄에 담는다. 비율은 분모를 함께 적는다 — 분모 없는 비율은 판단에 못 쓴다.
     *
     * 퍼널은 탈출 칸을 그대로 이어 붙인다. 로그에서 합이 설정 건수와 맞는지 눈으로 대조할 수
     * 있어야 분류 규칙이 틀어진 것을 화면 없이도 알아챈다.
     */
    private fun format(snapshot: InspectionMetricsResult): String {
        val funnel = snapshot.funnel()
        val stage1 = funnel.stage1()
        val stage2 = funnel.stage2()
        val stage3 = funnel.stage3()
        val coverage = snapshot.coverage()
        val cost = snapshot.cost()
        val backlog = snapshot.backlog()
        return listOf(
            "창=${snapshot.window().windowHours()}시간",
            "설정=${funnel.settledCount()}건(통과율=${rate(snapshot.judgment().passRate())}" +
                " 미분류=${funnel.unclassifiedCount()} 만료표식=${funnel.expiredMarkerCount()})",
            "탈출=" + funnel.exits().joinToString(",") { "${it.label()}:${it.count()}" },
            "1단차단율=${rate(stage1.blockRate())}(${stage1.blockedCount()}/${stage1.enteredCount()})",
            "승급률=${rate(stage2.promotionRate())}(${stage2.promotedCount()}/${stage2.enteredCount()}" +
                " 손익분기=${rate(stage2.breakevenRate())} 선별실패=${stage2.callFailedCount()}" +
                " 규칙우회=${stage2.bypassedByRuleHitCount()})",
            "3단검출율=${rate(stage3.detectionRate())}(${stage3.violationFoundCount()}/${stage3.respondedCount()}" +
                " 응답없음=${stage3.enteredCount() - stage3.respondedCount()})",
            "4단뒤집힘율=${rate(funnel.stage4().overturnRate())}(${funnel.stage4().overturnedCount()}" +
                "/${funnel.stage4().enteredCount()} 비평관측=측정불가)",
            "누락률=${rate(coverage.unverifiedRate())}(${coverage.unverifiedCount()}/${coverage.settledCount()}" +
                " 미판정=${coverage.unjudgedCount()} 판정실패=${coverage.verdictFailedCount()}" +
                " 관점미검=${coverage.modelUnverifiedCount()})",
            "선별건너뜀통과=${coverage.triageSkippedPassCount()}",
            "비용=${cost.totalCostMicroUsd()}microUSD(호출건당=${cost.costPerInspectionMicroUsd() ?: "-"}" +
                " 설정건당=${cost.costPerSettledInspectionMicroUsd() ?: "-"} 선별=${cost.triageCostMicroUsd()}" +
                " 캐시미적중호출=${cost.cacheMissCallCount()} 단계별=${stageCost(snapshot)})",
            "관리자대기=${backlog.openCount()}건(최장=${backlog.oldestWaitMinutes() ?: "-"}분" +
                " 판정별=${backlog.openByLatestJudgment()})",
        ).joinToString(" ")
    }

    private fun stageCost(snapshot: InspectionMetricsResult): String =
        snapshot
            .cost()
            .byStage()
            .entries
            .joinToString(",") { (stage, value) -> "$stage:${value.costMicroUsd()}" }

    /** 분모가 0이면 비율이 없다. 0%로 적으면 "안 돌았다"와 "전부 건너뛰었다"가 같은 글자가 된다 */
    private fun rate(value: Double?): String = value?.let { "%.1f%%".format(it * 100) } ?: "미측정"
}
