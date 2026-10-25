package com.aechak.application.inspection.facade;

import com.aechak.application.inspection.port.InspectionMetricsQueryPort;
import com.aechak.application.inspection.port.InspectionMetricsWindow;
import com.aechak.application.inspection.usecase.InspectionMetricsUseCase;
import com.aechak.application.inspection.usecase.query.InspectionMetricsQuery;
import com.aechak.application.inspection.usecase.result.InspectionMetricsResult;
import com.aechak.common.error.BusinessException;
import com.aechak.domain.product.error.ProductErrorCode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link InspectionMetricsUseCase}의 유일한 구현체. {@code @Transactional} 경계는 여기 고정한다.
 *
 * <p><b>파사드 패키지에 둔다.</b> 셀러 실행 모듈은 {@code ...inspection.service} 하나만 스캔하므로
 * 여기 두면 셀러가 조립하지 않는다. 지표는 관리자 전용이고, 셀러가 읽기 트랜잭션 경계를 쥔 집계
 * 빈을 집을 이유가 없다. 어드민은 {@code ...inspection} 전체를 스캔하므로 그대로 집는다.
 *
 * <p>한 스냅샷의 집계를 전부 한 트랜잭션에서 읽는다. 갈라 읽으면 그 사이에 판정이 적혀 퍼널의
 * 분모와 비용의 분모가 다른 시점을 보게 된다.
 *
 * <p><b>집계 테이블을 두지 않고 질의 시점에 센다.</b> 판정은 비동기이고 기한 정리가 10분 뒤에
 * 닫으므로 버킷을 굳히면 그 버킷이 나중에 틀려지고, 관리자 결정·셀러 이의는 며칠 뒤 같은 행에
 * 붙어 과거 버킷을 다시 쓰게 만든다. 대신 짧은 캐시를 둔다 — 주기 로그가 10분이고 화면
 * 새로고침이 전수 집계를 다시 돌릴 이유가 없다. 캐시는 <b>기본 창 스냅샷에만</b> 건다. 창·축을
 * 지정한 조회까지 캐시하면 키가 사실상 무한해져 메모리가 요청 수만큼 자란다.
 *
 * <p>창 폭에 상한을 둔다. 스냅샷의 창은 옵셔널이라 상한이 없으면 10년 창을 요청하는 것으로
 * 전수 스캔을 그대로 만들 수 있다.
 *
 * <p>임계·손익분기값은 전부 설정이다. 손익분기 승급률의 정본은 AI 레포의 측정 하네스이고, 그
 * 값이 코드에 박히면 전제가 바뀐 뒤에도 옛 기준으로 경보한다.
 */
@Service
public class InspectionMetricsFacade implements InspectionMetricsUseCase {

    private final InspectionMetricsQueryPort metricsQueryPort;
    private final int windowHours;
    private final double promotionBreakevenRate;
    private final long backlogWarnCount;
    private final long backlogWarnAgeMinutes;
    private final int maxWindowDays;
    private final long snapshotCacheSeconds;

    /** 기본 창 스냅샷의 짧은 캐시. 값과 만료 시각을 함께 갈아 끼워 둘이 어긋나지 않게 한다 */
    private final AtomicReference<CachedSnapshot> cachedSnapshot = new AtomicReference<>();

    @SuppressWarnings("checkstyle:ParameterNumber")
    public InspectionMetricsFacade(
            InspectionMetricsQueryPort metricsQueryPort,
            @Value("${inspection.metrics.window-hours:24}") int windowHours,
            @Value("${inspection.metrics.promotion-breakeven-rate:0.620}") double promotionBreakevenRate,
            @Value("${inspection.metrics.backlog-warn-count:50}") long backlogWarnCount,
            @Value("${inspection.metrics.backlog-warn-age-minutes:1440}") long backlogWarnAgeMinutes,
            @Value("${inspection.metrics.max-window-days:92}") int maxWindowDays,
            @Value("${inspection.metrics.snapshot-cache-seconds:60}") long snapshotCacheSeconds) {
        this.metricsQueryPort = metricsQueryPort;
        this.windowHours = windowHours;
        this.promotionBreakevenRate = promotionBreakevenRate;
        this.backlogWarnCount = backlogWarnCount;
        this.backlogWarnAgeMinutes = backlogWarnAgeMinutes;
        this.maxWindowDays = maxWindowDays;
        this.snapshotCacheSeconds = snapshotCacheSeconds;
    }

    @Transactional(readOnly = true)
    @Override
    public InspectionMetricsResult snapshot() {
        LocalDateTime now = LocalDateTime.now();
        CachedSnapshot cached = cachedSnapshot.get();
        if (cached != null && cached.expiresAt().isAfter(now)) {
            return cached.value();
        }

        // collect를 직접 부른다. 같은 빈의 public 메서드를 거치면 프록시를 타지 않아
        // 집계 전부가 트랜잭션 밖에서 돌고, 분모마다 다른 시점을 보게 된다.
        InspectionMetricsResult fresh = collect(resolveWindow(InspectionMetricsQuery.defaults(), now), now);
        cachedSnapshot.set(new CachedSnapshot(fresh, now.plusSeconds(snapshotCacheSeconds)));
        return fresh;
    }

    @Transactional(readOnly = true)
    @Override
    public InspectionMetricsResult snapshot(InspectionMetricsQuery query) {
        LocalDateTime now = LocalDateTime.now();
        return collect(resolveWindow(query, now), now);
    }

    /**
     * 집계 하나를 모은다.
     *
     * <p>적체만 창을 보지 않는다. 24시간보다 오래 열려 있는 건이 빠지면 가장 오래 기다린 건이
     * 지표에서 사라지고, 그게 제일 먼저 봐야 하는 건이다.
     */
    private InspectionMetricsResult collect(InspectionMetricsWindow window, LocalDateTime measuredAt) {
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
                metricsQueryPort.backlogCounts());
    }

    /** 비운 창을 설정 창으로 채우고 폭을 검증한다 */
    private InspectionMetricsWindow resolveWindow(InspectionMetricsQuery query, LocalDateTime now) {
        LocalDateTime to = query.to() == null ? now : query.to();
        LocalDateTime from = query.from() == null ? to.minusHours(windowHours) : query.from();
        requireBoundedWindow(from, to);
        return new InspectionMetricsWindow(from, to, query.policyVersion(), query.targetType());
    }

    /**
     * 창이 있고 폭이 상한 안인지 본다.
     *
     * <p>{@code from == to}도 거부한다. 폭이 0이면 모든 분모가 0이 되어 화면이 "미측정"으로
     * 채워지는데, 그것이 데이터가 없다는 뜻인지 창을 잘못 줬다는 뜻인지 구분되지 않는다.
     */
    private void requireBoundedWindow(LocalDateTime from, LocalDateTime to) {
        if (from == null || to == null || !from.isBefore(to)) {
            throw new BusinessException(ProductErrorCode.INSPECTION_METRICS_WINDOW_INVALID, null, null);
        }
        if (Duration.between(from, to).toDays() > maxWindowDays) {
            throw new BusinessException(ProductErrorCode.INSPECTION_METRICS_WINDOW_INVALID, null, null);
        }
    }

    private record CachedSnapshot(InspectionMetricsResult value, LocalDateTime expiresAt) {}
}
