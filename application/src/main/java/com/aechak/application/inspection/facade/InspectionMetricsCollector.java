package com.aechak.application.inspection.facade;

import com.aechak.application.inspection.port.InspectionMetricsQueryPort;
import com.aechak.application.inspection.port.InspectionMetricsWindow;
import com.aechak.application.inspection.usecase.result.InspectionMetricsResult;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 스냅샷 한 장의 집계를 읽는다. 읽기 트랜잭션 경계가 여기다.
 *
 * <p><b>파사드에서 떼어 낸 이유는 캐시 적중 경로다.</b> 트랜잭션이 파사드의 조회 메서드에
 * 붙어 있으면 프록시가 커넥션을 먼저 잡고 그 안에서 캐시를 읽어 그대로 돌려준다 — 쿼리 하나
 * 없이 풀에서 한 자리를 빌렸다 반납하는 셈이고, 어드민 기본 조회가 이 캐시를 타기 시작하면
 * 캐시의 이점이 그 비용으로 상쇄된다. 캐시는 트랜잭션 밖에서 읽고 미적중일 때만 이 빈을
 * 부른다.
 *
 * <p>한 스냅샷의 집계를 전부 한 트랜잭션에서 읽는다. 갈라 읽으면 그 사이에 판정이 적혀 퍼널의
 * 분모와 비용의 분모가 다른 시점을 보게 된다.
 *
 * <p>임계·손익분기값은 전부 설정이다. 손익분기 승급률의 정본은 AI 레포의 측정 하네스이고, 그
 * 값이 코드에 박히면 전제가 바뀐 뒤에도 옛 기준으로 경보한다.
 */
@Component
public class InspectionMetricsCollector {

    private final InspectionMetricsQueryPort metricsQueryPort;
    private final double promotionBreakevenRate;
    private final long backlogWarnCount;
    private final long backlogWarnAgeMinutes;

    public InspectionMetricsCollector(
            InspectionMetricsQueryPort metricsQueryPort,
            @Value("${inspection.metrics.promotion-breakeven-rate:0.620}") double promotionBreakevenRate,
            @Value("${inspection.metrics.backlog-warn-count:50}") long backlogWarnCount,
            @Value("${inspection.metrics.backlog-warn-age-minutes:1440}") long backlogWarnAgeMinutes) {
        this.metricsQueryPort = metricsQueryPort;
        this.promotionBreakevenRate = promotionBreakevenRate;
        this.backlogWarnCount = backlogWarnCount;
        this.backlogWarnAgeMinutes = backlogWarnAgeMinutes;
    }

    /**
     * 집계 하나를 모은다.
     *
     * <p>적체만 창을 보지 않는다. 24시간보다 오래 열려 있는 건이 빠지면 가장 오래 기다린 건이
     * 지표에서 사라지고, 그게 제일 먼저 봐야 하는 건이다. 대신 <b>축은 넘긴다</b> — 이 큐는
     * 상품만 담으므로 리뷰 축으로 좁힌 요청에 상품 수치와 그 수치로 만든 경보가 함께 실리면
     * 관리자가 "리뷰 검수 대기 N건, 임계 초과"를 읽는데 그 큐에는 리뷰가 한 건도 없다.
     */
    @Transactional(readOnly = true)
    public InspectionMetricsResult collect(InspectionMetricsWindow window, LocalDateTime measuredAt) {
        Map<String, Long> promptVersions = metricsQueryPort.promptVersionCounts(window);
        return InspectionMetricsResult.of(
                window,
                measuredAt,
                promptVersions,
                promotionBreakevenRate,
                backlogWarnCount,
                backlogWarnAgeMinutes,
                metricsQueryPort.funnelCounts(window),
                metricsQueryPort.triageCounts(window),
                metricsQueryPort.coverageCounts(window),
                metricsQueryPort.costCounts(window),
                metricsQueryPort.stageCosts(window),
                metricsQueryPort.backlogCounts(window));
    }
}
