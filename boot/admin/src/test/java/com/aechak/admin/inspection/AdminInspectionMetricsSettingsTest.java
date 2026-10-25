package com.aechak.admin.inspection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * [단위] 지표 임계가 실행 모듈 사이에서 갈라지지 않는지 고정한다.
 *
 * <p>어드민은 지표를 조회만 하고 주기 로그는 api가 찍는다. 두 모듈이 같은 값을 읽어야 화면과
 * 로그의 경보가 어긋나지 않는데, 설정이 각 모듈에 따로 적혀 있어 한쪽만 고치기 쉽다.
 *
 * <p>손익분기 승급률의 정본은 AI 레포의 측정 하네스({@code harness/run.py}의
 * {@code BREAKEVEN_PROMOTION_RATE})다. 실제로 그 값이 세 숫자(0.457·0.620·0.88)로 유통되던
 * 적이 있었고, 낮은 값을 쓰면 승급률 45.7~62.0% 구간에서 "손익분기 초과, 선별을 끄라"는 거짓
 * 경보가 난다 — 아직 이득인 구간인데 기능을 끄라고 말하는 셈이다. 하네스를 다시 돌려 값이
 * 바뀌면 이 테스트와 api 쪽 같은 이름의 테스트가 함께 깨지고, 그때 두 설정과 유스케이스
 * 기본값을 한 번에 고치게 된다.
 *
 * <p>프로파일별 파일이 아니라 모듈 공통 설정({@code application.yml})을 본다. 지표 임계는
 * 환경이 아니라 정책이고, 프로파일마다 적으면 local만 고친 상태가 그대로 dev로 나간다.
 */
class AdminInspectionMetricsSettingsTest {

    /** 정본은 측정 하네스다. api 모듈의 같은 상수와 반드시 같은 값이어야 한다 */
    private static final double BREAKEVEN_RATE = 0.620;

    private static final Pattern BREAKEVEN_LINE = Pattern.compile("promotion-breakeven-rate:\\s*([0-9.]+)");

    @Test
    void 손익분기_승급률이_하네스_정본과_같다() throws IOException {
        Matcher matcher = BREAKEVEN_LINE.matcher(readResource("application.yml"));
        assertTrue(matcher.find(), "application.yml 에 promotion-breakeven-rate 설정이 없다");
        assertEquals(
                BREAKEVEN_RATE, Double.parseDouble(matcher.group(1)), "손익분기 승급률이 정본과 다르다. 화면과 주기 로그가 다른 기준으로 경보한다");
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
}
