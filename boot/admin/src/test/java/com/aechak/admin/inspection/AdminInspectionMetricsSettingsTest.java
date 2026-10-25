package com.aechak.admin.inspection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aechak.application.inspection.facade.InspectionMetricsCollector;
import com.aechak.application.inspection.port.InspectionMetricsQueryPort;
import com.aechak.application.inspection.port.InspectionMetricsWindow;
import com.aechak.application.inspection.port.view.InspectionBacklogCounts;
import com.aechak.application.inspection.port.view.InspectionCostCounts;
import com.aechak.application.inspection.port.view.InspectionCoverageCounts;
import com.aechak.application.inspection.port.view.InspectionFunnelCounts;
import com.aechak.application.inspection.port.view.InspectionStageCost;
import com.aechak.application.inspection.port.view.InspectionTriageCounts;
import com.aechak.application.inspection.usecase.InspectionTriageCostModel;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * [단위] 지표 임계가 실행 모듈 사이에서 갈라지지 않는지 고정한다.
 *
 * <p>어드민은 지표를 조회만 하고 주기 로그는 api가 찍는다. 두 모듈이 같은 값을 읽어야 화면과
 * 로그의 경보가 어긋나지 않는데, 설정이 각 모듈에 따로 적혀 있어 한쪽만 고치기 쉽다.
 *
 * <p>손익분기 승급률의 사본은 이 레포에 하나뿐이다({@link InspectionTriageCostModel}). 그 정본은
 * AI 레포의 측정 하네스이고 두 레포를 묶는 CI 잡이 없으므로, <b>이 테스트는 하네스와의 일치를
 * 보증하지 않는다</b> — 보는 것은 모듈 설정이 이 레포의 사본과 같은지다. 하네스 값이 바뀌면 사본을
 * 손으로 고치고, 고치는 자리가 하나라 두 모듈이 서로 다른 기준으로 경보하는 모양은 생기지 않는다.
 *
 * <p>프로파일별 파일이 아니라 모듈 공통 설정({@code application.yml})을 본다. 지표 임계는
 * 환경이 아니라 정책이고, 프로파일마다 적으면 local만 고친 상태가 그대로 dev로 나간다.
 */
class AdminInspectionMetricsSettingsTest {

    private static final Pattern BREAKEVEN_LINE = Pattern.compile("promotion-breakeven-rate:\\s*([0-9.]+)");

    @Test
    void 손익분기_승급률이_레포_사본과_같다() throws IOException {
        Matcher matcher = BREAKEVEN_LINE.matcher(readResource("application.yml"));
        assertTrue(matcher.find(), "application.yml 에 promotion-breakeven-rate 설정이 없다");
        assertEquals(
                InspectionTriageCostModel.BREAKEVEN_PROMOTION_RATE,
                Double.parseDouble(matcher.group(1)),
                "손익분기 승급률이 레포 사본과 다르다. 화면과 주기 로그가 다른 기준으로 경보한다");
    }

    /** 비용 전제와 손익분기가 서로를 부정하지 않는지 본다. 셋 가운데 하나만 고치면 여기서 깨진다 */
    @Test
    void 손익분기_사본이_적어_둔_비용_전제에서_나온_값이다() {
        assertEquals(
                InspectionTriageCostModel.BREAKEVEN_PROMOTION_RATE,
                InspectionTriageCostModel.breakevenFromCostModel(),
                0.0005,
                "손익분기가 비용 전제(선별 1회 / 관점 둘)에서 나오지 않는다. 숫자 하나만 고친 상태다");
    }

    /**
     * 손익분기 설정이 빠지면 부팅에서 터진다.
     *
     * <p>기본값을 두면 설정 누락이 조용히 옛 기준으로 경보하고, 그 리터럴이 손익분기값의 또 한
     * 자리 사본이 된다. 값이 다섯 자리에 흩어져 있던 원인이 그 기본값이었다.
     */
    @Test
    void 손익분기_설정이_없으면_집계_빈이_조립되지_않는다() {
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(PropertyPlaceholderAutoConfiguration.class))
                .withBean(InspectionMetricsQueryPort.class, StubQueryPort::new)
                .withBean(InspectionMetricsCollector.class);

        runner.run(context -> assertTrue(context.getStartupFailure() != null, "손익분기 설정이 없는데도 집계 빈이 기본값으로 조립됐다"));

        runner.withPropertyValues("inspection.metrics.promotion-breakeven-rate=0.620")
                .run(context -> assertTrue(context.getStartupFailure() == null, "설정을 주면 집계 빈이 조립돼야 한다"));
    }

    /** 임계·창 설정이 빠지지 않는지 본다. 빠지면 기본값으로 떨어져 모듈 간에 동작이 갈린다 */
    @Test
    void 지표_설정이_빠지지_않는다() throws IOException {
        String text = readResource("application.yml");
        for (String key : List.of(
                "window-hours",
                "backlog-warn-count",
                "backlog-warn-age-minutes",
                "max-window-days",
                "snapshot-cache-seconds")) {
            assertTrue(text.contains(key + ":"), "application.yml 에 inspection.metrics." + key + " 가 없다");
        }
    }

    private String readResource(String name) throws IOException {
        try (InputStream stream = getClass().getResourceAsStream("/" + name)) {
            assertNotNull(stream, "설정 리소스를 찾지 못했다: " + name);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** 조립만 보는 테스트라 한 건도 호출되지 않는다 */
    private static final class StubQueryPort implements InspectionMetricsQueryPort {

        @Override
        public InspectionFunnelCounts funnelCounts(InspectionMetricsWindow window) {
            throw new UnsupportedOperationException();
        }

        @Override
        public InspectionTriageCounts triageCounts(InspectionMetricsWindow window) {
            throw new UnsupportedOperationException();
        }

        @Override
        public InspectionCoverageCounts coverageCounts(InspectionMetricsWindow window) {
            throw new UnsupportedOperationException();
        }

        @Override
        public InspectionCostCounts costCounts(InspectionMetricsWindow window) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Map<String, InspectionStageCost> stageCosts(InspectionMetricsWindow window) {
            throw new UnsupportedOperationException();
        }

        @Override
        public InspectionBacklogCounts backlogCounts(InspectionMetricsWindow window) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Map<String, Long> promptVersionCounts(InspectionMetricsWindow window) {
            throw new UnsupportedOperationException();
        }
    }
}
