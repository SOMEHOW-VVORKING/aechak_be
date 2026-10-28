package com.aechak.admin.inspection;

import static org.hamcrest.Matchers.closeTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aechak.domain.inspection.enums.InspectionJudgment;
import com.aechak.domain.inspection.enums.InspectionPerspectiveKind;
import com.aechak.domain.inspection.enums.PerspectiveOutcome;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/**
 * [통합] 단계별 통과율의 <b>분모와 분자</b>를 고정한다.
 *
 * <p>비율 지표는 분모를 잘못 잡아도 그럴듯한 숫자가 나와서 눈으로는 못 잡는다. 그래서 이
 * 파일의 각 테스트는 <b>분모에 들어오면 안 되는 건을 일부러 섞는다</b> — 미판정이 1단 분모에,
 * 레이트 리밋 건과 스텁 건과 선별이 건너뛴 건이 3단 분모에 들어오면 값이 달라지도록 수치를 골랐다.
 */
@TestPropertySource(
        properties = {
            "inspection.metrics.window-hours=24",
            "inspection.metrics.promotion-breakeven-rate=0.620",
            "inspection.metrics.backlog-warn-count=50",
            "inspection.metrics.backlog-warn-age-minutes=1440",
            // 캐시를 끈다. 테스트마다 다른 데이터를 심으므로 기본 창 스냅샷이 캐시되면 섞인다
            "inspection.metrics.snapshot-cache-seconds=0",
        })
class AdminInspectionFunnelMetricsIntegrationTest extends InspectionMetricsTestBase {

    private static final String BASE = "/api/v1/admin/inspection-metrics";

    /**
     * 탈출을 일곱으로 못박는다.
     *
     * <p>파이프라인이 말하는 탈출은 다섯인데 터미널은 일곱이다 — 판정 실패와 미판정이 더 있다.
     * 다섯만 세면 합이 설정 건수에 못 미쳐 "몇 퍼센트가 어디서 끝나는가"의 분모가 깨진다.
     */
    @Test
    void 탈출_분포는_일곱_버킷이고_합이_설정_건수와_맞는다() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        ruleBlocked(now);
        triageCleared(now);
        modelClear(now);
        criticOverturned(now);
        modelFlag(now);
        verdictFailed(now);
        unjudged(now);

        mockMvc.perform(bearer(get(BASE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.funnel.settledCount").value(7))
                .andExpect(jsonPath("$.data.funnel.unclassifiedCount").value(0))
                .andExpect(jsonPath("$.data.funnel.exits.length()").value(7))
                // 순서가 고정이다. 화면이 세로 퍼널로 그대로 그린다.
                .andExpect(jsonPath("$.data.funnel.exits[0].exit").value("RULE_BLOCKED"))
                .andExpect(jsonPath("$.data.funnel.exits[0].stage").value(1))
                .andExpect(jsonPath("$.data.funnel.exits[0].count").value(1))
                .andExpect(
                        jsonPath("$.data.funnel.exits[0].shareOfSettled").value(closeTo(1.0 / 7, 1e-9), Double.class))
                .andExpect(jsonPath("$.data.funnel.exits[1].exit").value("TRIAGE_CLEARED"))
                .andExpect(jsonPath("$.data.funnel.exits[1].count").value(1))
                .andExpect(jsonPath("$.data.funnel.exits[2].exit").value("MODEL_CLEAR"))
                .andExpect(jsonPath("$.data.funnel.exits[2].count").value(1))
                .andExpect(jsonPath("$.data.funnel.exits[3].exit").value("CRITIC_OVERTURNED"))
                .andExpect(jsonPath("$.data.funnel.exits[3].count").value(1))
                .andExpect(jsonPath("$.data.funnel.exits[4].exit").value("MODEL_FLAG"))
                .andExpect(jsonPath("$.data.funnel.exits[4].count").value(1))
                // 실패 터미널 둘은 퍼널 밖이다. stage 0으로 표시해 화면이 별도 칸으로 그린다.
                .andExpect(jsonPath("$.data.funnel.exits[5].exit").value("VERDICT_FAILED"))
                .andExpect(jsonPath("$.data.funnel.exits[5].stage").value(0))
                .andExpect(jsonPath("$.data.funnel.exits[6].exit").value("UNJUDGED"))
                .andExpect(jsonPath("$.data.funnel.exits[6].stage").value(0))
                // 교차 검증 — PASS = 선별통과 + 위반없음 + 뒤집힘, FLAG = 룰차단 + 확정위반
                .andExpect(jsonPath("$.data.judgment.settledCount").value(7))
                .andExpect(jsonPath("$.data.judgment.passCount").value(3))
                .andExpect(jsonPath("$.data.judgment.flagCount").value(2))
                .andExpect(jsonPath("$.data.judgment.unjudgedCount").value(1))
                .andExpect(jsonPath("$.data.judgment.failedCount").value(1))
                .andExpect(jsonPath("$.data.judgment.passRate").value(closeTo(3.0 / 7, 1e-9), Double.class))
                .andExpect(jsonPath("$.data.funnel.stage5.passCount").value(3))
                .andExpect(jsonPath("$.data.funnel.stage5.flagCount").value(2))
                .andExpect(jsonPath("$.data.needsAttention").value(false));
    }

    /**
     * 1단 차단율의 분모에서 미판정을 뺀다.
     *
     * <p>미판정 건은 AI 응답을 받은 적이 없어 규칙 단계를 탄 적도 없다 — 기한 정리가 미검사 행
     * 하나만 적고 닫는다. 분모에 넣으면 모델 장애가 룰 차단율을 끌어내려, 룰을 손볼 근거가
     * 장애 기간에 사라진다.
     *
     * <p>수치를 그 차이가 보이게 골랐다. 미판정을 넣으면 1/4 = 25%, 빼면 1/2 = 50%다.
     */
    @Test
    void 일단_차단율의_분모에서_미판정을_뺀다() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        ruleBlocked(now);
        modelClear(now);
        unjudged(now);
        unjudged(now);

        mockMvc.perform(bearer(get(BASE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.funnel.settledCount").value(4))
                .andExpect(jsonPath("$.data.funnel.stage1.name").value("RULE"))
                .andExpect(jsonPath("$.data.funnel.stage1.enteredCount").value(2))
                .andExpect(jsonPath("$.data.funnel.stage1.blockedCount").value(1))
                .andExpect(jsonPath("$.data.funnel.stage1.blockRate").value(0.5));
    }

    /**
     * 3단 검출율의 분모는 "3단을 탄 건"이 아니라 "응답이 온 건"이다.
     *
     * <p>레이트 리밋으로 못 본 건을 분모에 넣으면 검출율이 모델 품질이 아니라 가용성을 재게
     * 된다. 선별이 통과시킨 건은 모델 관점 행이 있어도 3단에 들어온 것이 아니고, 스텁 모드 건은
     * 호출이 한 번도 나가지 않았으므로 더더욱 아니다 — 폐기 설계가 그 건을 판정값으로 걸러
     * 들여보냈다(스텁의 판정은 미판정이 아니라 실패다).
     *
     * <p>수치: 응답 온 건 셋 중 둘이 위반이라 2/3이다. 분모를 "모델 행이 있는 건"으로 잡으면
     * 2/6, 스텁만 빠뜨리면 2/5, 선별 통과까지 넣으면 2/5다.
     */
    @Test
    void 삼단_검출율의_분모는_호출이_나간_검수다() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        criticOverturned(now);
        modelFlag(now);
        modelClear(now);
        // 레이트 리밋으로 모델 관점이 못 본 건. 호출은 나갔지만 응답은 없다.
        modelUnchecked(now);
        // 선별이 통과시킨 건. 모델 관점 행이 있지만 호출이 나가지 않았다.
        triageCleared(now);
        // 스텁 모드. ALL 행이 있지만 호출이 한 번도 나가지 않았다.
        stubbed(now);

        mockMvc.perform(bearer(get(BASE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.funnel.settledCount").value(6))
                .andExpect(jsonPath("$.data.funnel.stage3.name").value("JUDGE"))
                .andExpect(jsonPath("$.data.funnel.stage3.enteredCount").value(4))
                .andExpect(jsonPath("$.data.funnel.stage3.respondedCount").value(3))
                .andExpect(jsonPath("$.data.funnel.stage3.violationFoundCount").value(2))
                .andExpect(jsonPath("$.data.funnel.stage3.clearCount").value(1))
                .andExpect(jsonPath("$.data.funnel.stage3.detectionRate").value(closeTo(2.0 / 3, 1e-9), Double.class));
    }

    /**
     * 4단 뒤집힘율의 분모는 모델이 위반을 찾은 건이고, 비평 관측은 지금 측정 불가다.
     *
     * <p>뒤집힘에도 모델 위반 관점 행이 남는다 — 파이프라인이 위반 목록만 비우고 관점 행은
     * 되돌리지 않는다. 그래서 분모가 뒤집힘에 줄어들지 않는다.
     *
     * <p>비평 호출이 실제로 나갔는지는 가를 수 없다. 원장의 관점 값이 단일 판정·비평·수정 문구
     * 셋에 같은 {@code ALL}을 쓰기 때문이다. 비평이 꺼져 있으면 분자가 0이 되어 "비평이
     * 쓸모없다"는 반대 결론이 나오므로, 응답이 그 수치를 믿을 수 없다고 스스로 말해야 한다.
     */
    @Test
    void 사단_뒤집힘율은_모델_위반을_분모로_하고_비평_관측은_측정_불가로_남는다() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        long overturned = criticOverturned(now);
        // 비평 호출이 실제로 나간 건. 전용 관점이 없어 단일 판정 호출과 같은 값으로 적힌다.
        ledger(overturned, InspectionPerspectiveKind.ALL.name(), "SUCCESS", 7_000, 1_200, 5_900);
        modelFlag(now);
        modelFlag(now);
        modelFlag(now);

        mockMvc.perform(bearer(get(BASE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.funnel.stage4.name").value("CRITIC"))
                .andExpect(jsonPath("$.data.funnel.stage4.enteredCount").value(4))
                .andExpect(jsonPath("$.data.funnel.stage4.overturnedCount").value(1))
                .andExpect(jsonPath("$.data.funnel.stage4.upheldCount").value(3))
                .andExpect(jsonPath("$.data.funnel.stage4.overturnRate").value(0.25))
                .andExpect(jsonPath("$.data.funnel.stage4.escalationObserved").doesNotExist());
    }

    /**
     * 단계별 비용을 관점으로 쪼개고 {@code ALL} 행은 미귀속으로 남긴다.
     *
     * <p>비평이 가장 비싼 모델을 쓰므로 그 단계가 전체 지출의 몇 퍼센트인지 모르면 비평을 끌지
     * 말지를 숫자로 결정할 수 없다. 그런데 지금 {@code ALL}은 셋이 섞인 버킷이다 — 팬아웃을 끈
     * 단일 판정·비평·수정 문구. 숨기면 단계별 합이 총액에 못 미치는 이유를 알 수 없어서 따로 센다.
     */
    @Test
    void 단계별_비용은_관점으로_쪼개고_ALL_행은_미귀속으로_남긴다() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        long inspection = modelFlag(now);
        triageCall(inspection, "SUCCESS", 900);
        modelCall(inspection, InspectionPerspectiveKind.DISPLAY_ADS, 3_800);
        modelCall(inspection, InspectionPerspectiveKind.CONDUCT_TRADE, 3_700);
        // 비평·수정 문구·단일 판정이 같은 값으로 적힌다. 어느 단계인지 복원할 수 없다.
        ledger(inspection, InspectionPerspectiveKind.ALL.name(), "SUCCESS", 7_000, 1_200, 5_900);
        ledger(inspection, InspectionPerspectiveKind.ALL.name(), "SUCCESS", 1_500, 1_200, 1_870);

        mockMvc.perform(bearer(get(BASE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cost.totalCostMicroUsd").value(16_900))
                .andExpect(jsonPath("$.data.cost.byStage.TRIAGE.callCount").value(1))
                .andExpect(jsonPath("$.data.cost.byStage.TRIAGE.costMicroUsd").value(900))
                // 선별 접두부는 최소 캐시 길이에 못 미쳐 0이 아닌 것이 정상이다.
                .andExpect(jsonPath("$.data.cost.byStage.TRIAGE.cacheMissCallCount")
                        .value(1))
                .andExpect(
                        jsonPath("$.data.cost.byStage.DISPLAY_ADS.costMicroUsd").value(3_800))
                .andExpect(jsonPath("$.data.cost.byStage.CONDUCT_TRADE.costMicroUsd")
                        .value(3_700))
                // 관점 호출의 캐시 미적중은 0이 정상이고 0이 아니면 사건이다.
                .andExpect(jsonPath("$.data.cost.byStage.DISPLAY_ADS.cacheMissCallCount")
                        .value(0))
                .andExpect(
                        jsonPath("$.data.cost.byStage.UNATTRIBUTED.callCount").value(2))
                .andExpect(jsonPath("$.data.cost.byStage.UNATTRIBUTED.costMicroUsd")
                        .value(8_500))
                .andExpect(jsonPath("$.data.cost.byStage.ALL").doesNotExist());
    }

    /**
     * 관점 기록이 없는 검수는 미분류로 떨어지고 그 자체가 경보가 된다.
     *
     * <p>0이 정상이다. 0이 아니면 새 터미널이 생겼거나 분류 규칙이 실제 기록과 어긋났다는 뜻이고,
     * 그 상태를 지표가 조용히 삼키면 분포의 합이 설정 건수에 못 미치는 것을 아무도 모른다.
     */
    @Test
    void 분류되지_않는_검수는_미분류로_떨어지고_경보가_된다() throws Exception {
        bareReview(InspectionJudgment.PASS, LocalDateTime.now());

        mockMvc.perform(bearer(get(BASE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.funnel.settledCount").value(1))
                .andExpect(jsonPath("$.data.funnel.unclassifiedCount").value(1))
                .andExpect(jsonPath("$.data.needsAttention").value(true))
                .andExpect(jsonPath("$.data.alarms[?(@.code == 'UNCLASSIFIED_EXIT')]")
                        .exists());
    }

    /**
     * 기한 만료 표식과 미판정 건수가 어긋나면 경보로 올라간다.
     *
     * <p>만료 건을 분모에서 빼는 규약이 "표식을 직접 본다"에 기대고 있다. 그 전제가 깨지는 길이
     * 둘이다 — 기한 스윕이 검수 행만 닫고 관점 행을 못 적거나, 다른 경로가 미판정을 만들거나.
     * 둘 다 에러 없이 일어나므로 지표가 스스로 감시해야 한다.
     */
    @Test
    void 만료_표식과_미판정_건수가_어긋나면_경보가_된다() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        // 표식 없이 미판정으로 닫힌 행. 기한 스윕이 관점 행을 못 적은 모양이다.
        long withoutMarker = bareReview(InspectionJudgment.UNJUDGED, now);
        finding(withoutMarker, InspectionPerspectiveKind.RULE, PerspectiveOutcome.CLEAR, null);
        unjudged(now);

        mockMvc.perform(bearer(get(BASE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.funnel.stage5.unjudgedCount").value(2))
                .andExpect(jsonPath("$.data.funnel.expiredMarkerCount").value(1))
                .andExpect(jsonPath("$.data.alarms[?(@.code == 'EXPIRED_MARKER_MISMATCH')]")
                        .exists());
    }

    /**
     * 기한 만료 표식은 <b>세 플래그 모두에서</b> 모델이 본 것으로 세지 않는다.
     *
     * <p>누락률의 증거 술어가 노출 게이트의 판단을 베낀 것인데, 미검사 플래그 하나만 그 표식을
     * 제외하지 않아 둘이 갈라졌다. 표식이 적힌 뒤 같은 행에 판정이 적히는 날(그 어긋남을
     * 감시하려고 만든 경보가 가리키는 바로 그 상황) 선별이 통과시킨 건이 누락으로 넘어가는데,
     * 메시지만 보는 게이트는 저장된 표식을 못 보므로 "증거 있음"이라고 답한다 — 거울을 만든
     * 목적인 "지표는 누락이라는데 큐에는 안 올라옴"이 그대로 재현된다.
     */
    @Test
    void 만료_표식이_남은_선별_통과도_증거_있는_통과로_센다() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        long cleared = triageCleared(now);
        finding(cleared, InspectionPerspectiveKind.ALL, PerspectiveOutcome.UNCHECKED, EXPIRED_SKIP_REASON);

        mockMvc.perform(bearer(get(BASE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.coverage.settledCount").value(1))
                .andExpect(jsonPath("$.data.coverage.modelUnverifiedCount").value(0))
                .andExpect(jsonPath("$.data.coverage.unverifiedCount").value(0));
    }

    /**
     * 사유 없는 미검사 기록은 경보가 된다.
     *
     * <p>선별이 일부러 건너뛴 것과 호출이 죽어 못 본 것을 가르는 축이 사유 하나뿐이다. 사유가
     * 비면 그 건은 어느 쪽으로도 셀 수 없고, 그 상태를 삼키면 "통과"와 "미검사"가 섞인다.
     */
    @Test
    void 사유_없는_미검사_기록은_경보가_된다() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        long noReason = bareReview(InspectionJudgment.PASS, now);
        finding(noReason, InspectionPerspectiveKind.RULE, PerspectiveOutcome.CLEAR, null);
        finding(noReason, InspectionPerspectiveKind.DISPLAY_ADS, PerspectiveOutcome.CLEAR, null);
        finding(noReason, InspectionPerspectiveKind.CONDUCT_TRADE, PerspectiveOutcome.UNCHECKED, null);

        mockMvc.perform(bearer(get(BASE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.funnel.missingSkipReasonCount").value(1))
                .andExpect(jsonPath("$.data.alarms[?(@.code == 'MISSING_SKIP_REASON')]")
                        .exists());
    }
}
