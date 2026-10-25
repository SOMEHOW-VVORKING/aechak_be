package com.aechak.admin.inspection;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/**
 * [통합] 기본 창 조회가 <b>캐시를 탄다</b>.
 *
 * <p>캐시가 걸린 경로와 어드민이 부르는 경로가 갈라져 있었다 — 질의 파라미터가 전부 비어도
 * 컨트롤러가 조회 객체를 만들어 넘기므로, 캐시는 10분 주기 로그만 쓰고 그 주기가 TTL보다 열 배
 * 길어 구조적으로 한 번도 적중하지 않았다. 결과는 관리자가 새로고침할 때마다 전수 집계가 그대로
 * 다시 도는 것이다. 적체까지 함께 세는 집계라 큐가 쌓인 상태에서 가장 무거워지는데, 인증만 있으면
 * 되는 GET이고 레이트 리밋이 없다.
 *
 * <p>캐시 유무로 관측 가능한 차이를 만들어 고정한다 — 첫 조회 뒤에 검수를 더 심고 다시 부르면
 * 수치가 그대로여야 캐시를 탄 것이다. 축을 준 조회는 같은 캐시를 타지 않는 것도 함께 본다.
 */
@TestPropertySource(
        properties = {
            "inspection.metrics.window-hours=24",
            "inspection.metrics.promotion-breakeven-rate=0.620",
            // 한 테스트가 끝날 때까지 만료되지 않을 만큼 길게 둔다. 지표 수치가 아니라
            // 캐시를 타는지만 보는 수트다.
            "inspection.metrics.snapshot-cache-seconds=600",
        })
class AdminInspectionMetricsCacheIntegrationTest extends InspectionMetricsTestBase {

    private static final String BASE = "/api/v1/admin/inspection-metrics";

    @Test
    void 창도_축도_비운_조회는_주기_로그와_같은_캐시를_탄다() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        modelClear(now, "policy-v1");

        mockMvc.perform(bearer(get(BASE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.funnel.settledCount").value(1));

        modelClear(now, "policy-v1");

        mockMvc.perform(bearer(get(BASE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.funnel.settledCount").value(1));
    }

    /**
     * 빈 문자열 축은 비운 축이다.
     *
     * <p>{@code ?policyVersion=} 는 스프링이 {@code null} 이 아니라 {@code ""} 로 바인딩한다
     * (열거·시각은 빈 값을 null 로 바꿔 주지만 문자열은 그대로다). 정규화하지 않으면 한 글자도
     * 없는 질의 파라미터 하나가 캐시 분기를 우회해 매 요청이 전수 집계를 다시 돌리고, 게다가
     * {@code policy_version = ''} 로 좁혀 퍼널이 전부 0인 응답에 전체 큐 적체가 실린다.
     */
    @Test
    void 빈_문자열_정책_버전은_비운_조회와_같은_캐시를_탄다() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        modelClear(now, "policy-v1");

        mockMvc.perform(bearer(get(BASE).param("policyVersion", "")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.window.policyVersion").doesNotExist())
                .andExpect(jsonPath("$.data.funnel.settledCount").value(1));

        modelClear(now, "policy-v1");

        mockMvc.perform(bearer(get(BASE).param("policyVersion", "")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.funnel.settledCount").value(1));
    }

    /** 축을 준 조회는 캐시하지 않는다. 키가 사실상 무한해져 메모리가 요청 수만큼 자란다 */
    @Test
    void 축을_준_조회는_캐시를_타지_않는다() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        modelClear(now, "policy-v1");

        mockMvc.perform(bearer(get(BASE).param("policyVersion", "policy-v1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.funnel.settledCount").value(1));

        modelClear(now, "policy-v1");

        mockMvc.perform(bearer(get(BASE).param("policyVersion", "policy-v1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.funnel.settledCount").value(2));
    }
}
