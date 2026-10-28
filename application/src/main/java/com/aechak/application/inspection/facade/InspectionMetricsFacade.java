package com.aechak.application.inspection.facade;

import com.aechak.application.inspection.port.InspectionMetricsWindow;
import com.aechak.application.inspection.usecase.InspectionMetricsUseCase;
import com.aechak.application.inspection.usecase.query.InspectionMetricsQuery;
import com.aechak.application.inspection.usecase.result.InspectionMetricsResult;
import com.aechak.common.error.BusinessException;
import com.aechak.domain.product.error.ProductErrorCode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * {@link InspectionMetricsUseCase}의 유일한 구현체. 창을 정하고 캐시를 들고, 집계는
 * {@link InspectionMetricsCollector}에 맡긴다.
 *
 * <p><b>파사드 패키지에 둔다.</b> 셀러 실행 모듈은 {@code ...inspection.service} 하나만 스캔하므로
 * 여기 두면 셀러가 조립하지 않는다. 지표는 관리자 전용이고, 셀러가 읽기 트랜잭션 경계를 쥔 집계
 * 빈을 집을 이유가 없다. 어드민은 {@code ...inspection} 전체를 스캔하므로 그대로 집는다.
 *
 * <p><b>트랜잭션을 여기에 두지 않는다.</b> 조회 메서드에 붙이면 프록시가 커넥션을 먼저 잡고 그
 * 안에서 캐시를 읽어 그대로 돌려준다 — 쿼리 하나 없이 풀에서 한 자리를 빌렸다 반납하는 셈이다.
 * 경계는 집계 빈이 쥔다.
 *
 * <p><b>집계 테이블을 두지 않고 질의 시점에 센다.</b> 판정은 비동기이고 기한 정리가 10분 뒤에
 * 닫으므로 버킷을 굳히면 그 버킷이 나중에 틀려지고, 관리자 결정·셀러 이의는 며칠 뒤 같은 행에
 * 붙어 과거 버킷을 다시 쓰게 만든다. 대신 짧은 캐시를 둔다.
 *
 * <p>캐시는 <b>기본 창 스냅샷에만</b> 건다. 창·축을 지정한 조회까지 캐시하면 키가 사실상 무한해져
 * 메모리가 요청 수만큼 자란다. 대신 <b>창도 축도 비운 조회는 기본 창 스냅샷과 같은 요청이라 같은
 * 캐시를 탄다</b> — 어드민 화면의 기본 조회가 그 모양이고, 그것이 캐시를 못 타면 새로고침 한 번이
 * 전수 집계를 그대로 다시 돌린다. 적체까지 함께 세는 집계라 큐가 쌓인 상태에서 가장 무거워지는데,
 * 인증만 있으면 되는 GET이고 레이트 리밋이 없다.
 *
 * <p>창 폭에 상한을 둔다. 스냅샷의 창은 옵셔널이라 상한이 없으면 10년 창을 요청하는 것으로
 * 전수 스캔을 그대로 만들 수 있다.
 */
@Service
public class InspectionMetricsFacade implements InspectionMetricsUseCase {

    private final InspectionMetricsCollector collector;
    private final int windowHours;
    private final int maxWindowDays;
    private final long snapshotCacheSeconds;

    /** 기본 창 스냅샷의 짧은 캐시. 값과 만료 시각을 함께 갈아 끼워 둘이 어긋나지 않게 한다 */
    private final AtomicReference<CachedSnapshot> cachedSnapshot = new AtomicReference<>();

    public InspectionMetricsFacade(
            InspectionMetricsCollector collector,
            @Value("${inspection.metrics.window-hours:24}") int windowHours,
            @Value("${inspection.metrics.max-window-days:92}") int maxWindowDays,
            @Value("${inspection.metrics.snapshot-cache-seconds:60}") long snapshotCacheSeconds) {
        this.collector = collector;
        this.windowHours = windowHours;
        this.maxWindowDays = maxWindowDays;
        this.snapshotCacheSeconds = snapshotCacheSeconds;
    }

    @Override
    public InspectionMetricsResult snapshot() {
        LocalDateTime now = LocalDateTime.now();
        CachedSnapshot cached = cachedSnapshot.get();
        if (cached != null && cached.expiresAt().isAfter(now)) {
            return cached.value();
        }

        InspectionMetricsResult fresh = collector.collect(resolveWindow(InspectionMetricsQuery.defaults(), now), now);
        cachedSnapshot.set(new CachedSnapshot(fresh, now.plusSeconds(snapshotCacheSeconds)));
        return fresh;
    }

    @Override
    public InspectionMetricsResult snapshot(InspectionMetricsQuery query) {
        // 비운 조회는 기본 창 스냅샷과 같은 요청이다. 같은 캐시를 타야 화면 새로고침이 전수
        // 집계를 다시 돌리지 않고, 화면과 주기 로그가 같은 한 장을 본다.
        if (query.isDefaults()) {
            return snapshot();
        }

        LocalDateTime now = LocalDateTime.now();
        return collector.collect(resolveWindow(query, now), now);
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
     *
     * <p>폭을 일 단위로 절삭해 비교하지 않는다. 절삭하면 상한이 하루 가까이 느슨해져(92일
     * 23시간 59분이 92로 계산된다) 상한을 둔 이유인 전수 스캔 방지가 그만큼 빈다.
     */
    private void requireBoundedWindow(LocalDateTime from, LocalDateTime to) {
        if (from == null || to == null || !from.isBefore(to)) {
            throw new BusinessException(ProductErrorCode.INSPECTION_METRICS_WINDOW_INVALID, null, null);
        }
        if (Duration.between(from, to).compareTo(Duration.ofDays(maxWindowDays)) > 0) {
            throw new BusinessException(ProductErrorCode.INSPECTION_METRICS_WINDOW_INVALID, null, null);
        }
    }

    private record CachedSnapshot(InspectionMetricsResult value, LocalDateTime expiresAt) {}
}
