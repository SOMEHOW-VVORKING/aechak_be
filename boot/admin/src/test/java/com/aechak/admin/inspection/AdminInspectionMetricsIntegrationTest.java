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
 * [통합] 승급률·누락률·비용·적체의 <b>분모</b>를 고정한다.
 *
 * <p>여기서 잡는 것은 <b>판단에 쓸 수 있는 수치가 나오는가</b>다. 1순위는 승급률이다. 손익분기
 * 위로 가면 선별은 비용을 줄이는 장치가 아니라 더하는 장치이고, 분모를 잘못 잡으면 그 판단이
 * 뒤집힌다.
 *
 * <p>검수 행과 호출 원장은 도메인·포트로 직접 심는다. 판정 수신 경로는 api 소유라 이 모듈에 없다.
 *
 * <p>적체 임계를 낮춰 둔다(건수 2·시간 60분). 기본값(50건·1440분)으로는 한 건을 심어 경보를
 * 재현할 수 없다.
 */
@TestPropertySource(
        properties = {
            "inspection.metrics.window-hours=24",
            "inspection.metrics.promotion-breakeven-rate=0.620",
            "inspection.metrics.backlog-warn-count=2",
            "inspection.metrics.backlog-warn-age-minutes=60",
            // 캐시를 끈다. 테스트마다 다른 데이터를 심는데 기본 창 스냅샷이 캐시되면
            // 뒤 테스트가 앞 테스트의 수치를 읽는다. 캐시 자체는 전용 수트가 본다.
            "inspection.metrics.snapshot-cache-seconds=0",
        })
class AdminInspectionMetricsIntegrationTest extends InspectionMetricsTestBase {

    private static final String BASE = "/api/v1/admin/inspection-metrics";

    /**
     * 승급률의 분모는 선별 호출이 나간 검수이고, 건너뛴 건은 관점 행이 {@code CLEAR}인 것으로 가른다.
     *
     * <p>규칙이 적중해 선별에 묻지도 않고 승급한 건에도 선별 관점 행이 남는다. 관점 행의 유무로
     * 분모·분자를 세면 호출하지도 않은 건이 분모에 들어오고, 승급 경로의 행({@code UNCHECKED})까지
     * 건너뜀으로 분류되어 1순위 지표가 뒤집힌다.
     *
     * <p>수치: 호출이 나간 건 셋 중 하나가 건너뜀이라 승급률 2/3 = 66.7%다. 손익분기 62.0%를
     * 넘으므로 경보가 함께 떠야 한다 — 규칙 우회 건을 분모에 넣으면 2/4 = 50%로 경보가 사라진다.
     */
    @Test
    void 승급률은_선별_호출이_나간_검수를_분모로_하고_손익분기_초과를_드러낸다() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        long skipped = triageCleared(now);
        triageCall(skipped, "SUCCESS", 900);

        long promoted = modelFlag(now);
        finding(promoted, InspectionPerspectiveKind.TRIAGE, PerspectiveOutcome.UNCHECKED, "PROMOTED");
        triageCall(promoted, "SUCCESS", 900);
        modelCall(promoted, InspectionPerspectiveKind.DISPLAY_ADS, 3_800);

        long forced = modelClear(now);
        finding(forced, InspectionPerspectiveKind.TRIAGE, PerspectiveOutcome.UNCHECKED, "PROMOTED");
        triageCall(forced, "RATE_LIMITED", 0);
        modelCall(forced, InspectionPerspectiveKind.DISPLAY_ADS, 3_800);

        // 규칙 적중으로 선별을 건너뛴 승급. 호출이 없으므로 분모에 들어오지 않는다.
        long ruleHit = modelFlag(now);
        finding(ruleHit, InspectionPerspectiveKind.TRIAGE, PerspectiveOutcome.UNCHECKED, RULE_HIT_SKIP_REASON);
        modelCall(ruleHit, InspectionPerspectiveKind.DISPLAY_ADS, 3_800);

        mockMvc.perform(bearer(get(BASE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.funnel.stage2.name").value("TRIAGE"))
                .andExpect(jsonPath("$.data.funnel.stage2.enteredCount").value(3))
                .andExpect(jsonPath("$.data.funnel.stage2.clearedCount").value(1))
                .andExpect(jsonPath("$.data.funnel.stage2.promotedCount").value(2))
                .andExpect(jsonPath("$.data.funnel.stage2.callFailedCount").value(1))
                // 분모가 왜 1단 통과 건수보다 작은지를 설명하는 수치. 없으면 건수가 사라진 것처럼 보인다.
                .andExpect(
                        jsonPath("$.data.funnel.stage2.bypassedByRuleHitCount").value(1))
                .andExpect(jsonPath("$.data.funnel.stage2.skipRate").value(closeTo(1.0 / 3, 1e-9), Double.class))
                .andExpect(jsonPath("$.data.funnel.stage2.promotionRate").value(closeTo(2.0 / 3, 1e-9), Double.class))
                .andExpect(jsonPath("$.data.funnel.stage2.breakevenRate").value(closeTo(0.620, 1e-9), Double.class))
                .andExpect(jsonPath("$.data.funnel.stage2.overBreakeven").value(true))
                .andExpect(jsonPath("$.data.needsAttention").value(true))
                .andExpect(
                        jsonPath("$.data.alarms[?(@.code == 'OVER_BREAKEVEN')]").exists());
    }

    /**
     * 선별이 한 건도 돌지 않았으면 승급률은 0%가 아니라 미측정이다.
     *
     * <p>지금 이것이 정상 상태다 — AI 쪽 선별 기본값이 꺼짐이라 분모가 0이다. 0%로 내려보내면
     * "안 돌았다"와 "돌았는데 전부 승급했다"가 같은 숫자가 되고, 켤지 말지를 그 숫자로 판단하면
     * 가장 위험한 쪽으로 틀린다.
     */
    @Test
    void 선별이_돌지_않았으면_승급률은_비어_온다() throws Exception {
        long onlyModel = modelClear(LocalDateTime.now());
        modelCall(onlyModel, InspectionPerspectiveKind.DISPLAY_ADS, 3_800);

        mockMvc.perform(bearer(get(BASE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.funnel.stage2.enteredCount").value(0))
                .andExpect(jsonPath("$.data.funnel.stage2.promotionRate").doesNotExist())
                .andExpect(jsonPath("$.data.funnel.stage2.skipRate").doesNotExist())
                .andExpect(jsonPath("$.data.funnel.stage2.overBreakeven").value(false));
    }

    /**
     * 누락률은 아무도 내용을 보지 않은 채 닫힌 비율이고, 선별이 건너뛴 건은 누락이 아니다.
     *
     * <p>이 테스트가 이 작업의 핵심 규약을 고정한다. 선별이 일부러 건너뛴
     * {@code TRIAGE_CLEAR}는 <b>판단</b>이고, 호출이 죽어 못 본 것은 <b>장애</b>다. 둘을 가르는
     * 축은 사유 하나뿐이라 사유가 규약이 된다. 섞으면 모델이 레이트 리밋으로 보지 못한 건이
     * "문제 없음"으로 집계되고 통과율이 부풀려진다.
     *
     * <p>그리고 선별이 통과시켰다는 사실만으로 증거가 되지 않는다. 선별이 가른 뒤에 모델 관점
     * 하나가 레이트 리밋으로 죽었으면 그 건은 선별의 판단이 아니라 장애다 — 사유가 섞여 있으면
     * 증거로 세지 않는다.
     *
     * <p>수치: 미판정 1 + 판정실패 1 + 증거없는통과 2 = 4, 설정 7건이라 4/7이다. 선별 통과를
     * 누락에 넣으면 5/7, 사유를 보지 않고 "선별 통과면 증거"로 세면 3/7이 된다.
     */
    @Test
    void 누락률은_선별이_건너뛴_통과와_호출이_죽은_미검사를_가른다() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        modelClear(now);
        modelFlag(now);
        unjudged(now);
        verdictFailed(now);

        // 통과인데 모델 관점이 전부 레이트 리밋으로 죽었다. 선별 기록도 없다 — 증거가 없다.
        long unverified = bareReview(InspectionJudgment.PASS, now);
        finding(unverified, InspectionPerspectiveKind.RULE, PerspectiveOutcome.CLEAR, null);
        finding(unverified, InspectionPerspectiveKind.DISPLAY_ADS, PerspectiveOutcome.UNCHECKED, "RATE_LIMITED");
        finding(unverified, InspectionPerspectiveKind.CONDUCT_TRADE, PerspectiveOutcome.UNCHECKED, "RATE_LIMITED");

        // 선별이 건너뛴 통과. 판단이라 증거로 센다.
        triageCleared(now);

        // 선별이 가른 뒤 관점 하나가 죽었다. 사유가 섞여 있어 증거가 아니다.
        long triagedButDead = bareReview(InspectionJudgment.PASS, now);
        finding(triagedButDead, InspectionPerspectiveKind.RULE, PerspectiveOutcome.CLEAR, null);
        finding(triagedButDead, InspectionPerspectiveKind.TRIAGE, PerspectiveOutcome.CLEAR, null);
        finding(
                triagedButDead,
                InspectionPerspectiveKind.DISPLAY_ADS,
                PerspectiveOutcome.UNCHECKED,
                TRIAGE_SKIP_REASON);
        finding(triagedButDead, InspectionPerspectiveKind.CONDUCT_TRADE, PerspectiveOutcome.UNCHECKED, "RATE_LIMITED");

        mockMvc.perform(bearer(get(BASE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.coverage.settledCount").value(7))
                .andExpect(jsonPath("$.data.coverage.unjudgedCount").value(1))
                .andExpect(jsonPath("$.data.coverage.verdictFailedCount").value(1))
                .andExpect(jsonPath("$.data.coverage.modelUnverifiedCount").value(2))
                .andExpect(jsonPath("$.data.coverage.unverifiedCount").value(4))
                .andExpect(jsonPath("$.data.coverage.unverifiedRate").value(closeTo(4.0 / 7, 1e-9), Double.class))
                // 선별이 건너뛴 통과는 모집단으로 따로 센다. 누락의 분자가 아니다.
                .andExpect(jsonPath("$.data.coverage.triageSkippedPassCount").value(2))
                // 증거 없는 통과는 파이프라인이 낼 수 없는 모양이라 탈출 분류에서도 떨어진다.
                .andExpect(jsonPath("$.data.funnel.unclassifiedCount").value(1))
                .andExpect(jsonPath("$.data.alarms[?(@.code == 'UNCLASSIFIED_EXIT')]")
                        .exists());
    }

    /**
     * 건당 비용의 분모가 둘이다.
     *
     * <p>원장 유래 분모는 재시도와 등급 승격이 부풀리지 않지만 <b>규칙이 무료로 차단한 건이
     * 빠진다</b>. 1단 차단율이 높을수록 그만큼 건당 비용이 비싸게 보인다. 그래서 창 안에 닫힌
     * 검수 전수를 분모로 한 수치를 함께 내려보낸다 — 선별을 켤지를 이 숫자로 판단한다.
     */
    @Test
    void 건당_비용은_호출이_나간_건당과_닫힌_검수_건당을_함께_내려준다() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        long first = triageCleared(now);
        triageCall(first, "SUCCESS", 900);

        long second = modelFlag(now);
        triageCall(second, "SUCCESS", 900);
        modelCall(second, InspectionPerspectiveKind.DISPLAY_ADS, 3_800);
        // 같은 검수의 다른 관점. 호출 수만 늘고 검수 수는 그대로여야 한다.
        modelCall(second, InspectionPerspectiveKind.CONDUCT_TRADE, 3_800);

        // 규칙이 무료로 차단한 건. 원장에 행이 없어 호출 유래 분모에서 빠진다.
        ruleBlocked(now);

        mockMvc.perform(bearer(get(BASE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cost.callCount").value(4))
                .andExpect(jsonPath("$.data.cost.inspectionCount").value(2))
                .andExpect(jsonPath("$.data.cost.totalCostMicroUsd").value(9_400))
                .andExpect(jsonPath("$.data.cost.triageCostMicroUsd").value(1_800))
                .andExpect(jsonPath("$.data.cost.costPerInspectionMicroUsd").value(4_700))
                // 닫힌 검수가 셋이므로 진짜 건당은 그만큼 싸다. 두 수치가 같으면 분모가 하나다.
                .andExpect(
                        jsonPath("$.data.cost.costPerSettledInspectionMicroUsd").value(3_133))
                // 선별 호출 둘만 캐시를 못 썼다 — 접두부가 최소 길이에 못 미치는 자리다.
                .andExpect(jsonPath("$.data.cost.cacheMissCallCount").value(2));
    }

    /**
     * 적체는 관리자 큐 목록과 같은 필터로 세고 닫힌 행은 빼고 센다.
     *
     * <p>목록 필터 없이 세면 지표가 "대기 N건"이라 말하는데 화면에는 없는 상태가 된다. 사람이
     * 처리할 수 있는 건수가 적체의 정의다.
     *
     * <p>임계를 둘 둔다. 건수만 보면 하루 한 건씩 쌓이는 큐가 임계에 닿지 않고, 시간만 보면
     * 사람이 오래된 것부터 치우는 동안 뒤에 쌓이는 양이 안 보인다. 여기서는 시간 임계만 넘는다.
     */
    @Test
    void 적체는_열린_행만_세고_가장_오래_기다린_시각을_준다() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        long blocked = settledProduct("c".repeat(64), InspectionJudgment.FLAG, now.minusMinutes(90));
        finding(blocked, InspectionPerspectiveKind.RULE, PerspectiveOutcome.VIOLATION, null);
        // 대기 시간의 축은 유입 시각이다. 정렬 키(blinded_at)가 아니라 created_at을 민다
        backdateQueueRow(openQueueRow(now.minusMinutes(90)), now.minusMinutes(90));
        // 관리자가 이미 결정한 행. 목록에서도 대기가 아니므로 적체에 들어오면 안 된다.
        approveQueueRow(openQueueRow(now.minusMinutes(30)), now);

        mockMvc.perform(bearer(get(BASE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.backlog.openCount").value(1))
                .andExpect(jsonPath("$.data.backlog.oldestWaitMinutes").value(90))
                .andExpect(jsonPath("$.data.backlog.openByLatestJudgment.FLAG").value(1))
                .andExpect(jsonPath("$.data.backlog.breached").value(true))
                .andExpect(jsonPath("$.data.needsAttention").value(true))
                .andExpect(jsonPath("$.data.alarms[?(@.code == 'BACKLOG_AGE')]").exists())
                .andExpect(
                        jsonPath("$.data.alarms[?(@.code == 'BACKLOG_COUNT')]").doesNotExist());
    }

    /**
     * 대기 시간은 <b>유입 시각</b>으로 잰다.
     *
     * <p>{@code blinded_at}으로 재면 안 된다. 그 칼럼은 "행을 열었거나 다시 연 판정의 시각"이고
     * 열린 행에 새 판정이 올 때마다 덮인다. 관리자가 한 번도 보지 않은 행인데 셀러가 내용을 고쳐
     * 매일 새 판정이 걸리면, 그 행은 매일 "방금부터 기다림"으로 되감겨 임계에 영원히 닿지 않는다.
     * 주기 로그가 존재하는 유일한 이유가 그 경보인데 그것이 구조적으로 발화하지 않는 상태가 된다.
     */
    @Test
    void 적체의_대기_시간은_유입_시각이라_재차단으로_되감기지_않는다() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        long blocked = settledProduct("c".repeat(64), InspectionJudgment.FLAG, now.minusMinutes(5));
        finding(blocked, InspectionPerspectiveKind.RULE, PerspectiveOutcome.VIOLATION, null);

        // 오래전에 열렸고 아무도 보지 않았는데, 방금 온 판정이 정렬 키를 현재로 끌어올렸다
        long itemId = openQueueRow(now.minusMinutes(5));
        backdateQueueRow(itemId, now.minusMinutes(5_000));

        mockMvc.perform(bearer(get(BASE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.backlog.openCount").value(1))
                .andExpect(jsonPath("$.data.backlog.oldestWaitMinutes").value(5_000))
                .andExpect(jsonPath("$.data.alarms[?(@.code == 'BACKLOG_AGE')]").exists());
    }

    /**
     * 적체는 상품 큐의 모양이라 리뷰 축 응답에 실리면 안 된다.
     *
     * <p>리뷰 검수는 노출 게이트가 조기 반환해 이 큐에 들어올 수 없다. 축을 무시하고 상품 수치를
     * 실으면 관리자는 "리뷰 검수 대기 N건, 임계 초과"를 읽는데 그 큐에는 리뷰가 한 건도 없다.
     */
    @Test
    void 리뷰_축으로_좁히면_상품_큐의_수치와_경보가_실리지_않는다() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        long blocked = settledProduct("c".repeat(64), InspectionJudgment.FLAG, now.minusMinutes(5));
        finding(blocked, InspectionPerspectiveKind.RULE, PerspectiveOutcome.VIOLATION, null);
        long itemId = openQueueRow(now.minusMinutes(5));
        backdateQueueRow(itemId, now.minusMinutes(5_000));

        mockMvc.perform(bearer(get(BASE).param("targetType", "PRODUCT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.backlog.openCount").value(1))
                .andExpect(jsonPath("$.data.alarms[?(@.code == 'BACKLOG_AGE')]").exists());

        mockMvc.perform(bearer(get(BASE).param("targetType", "REVIEW")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.window.targetType").value("REVIEW"))
                .andExpect(jsonPath("$.data.backlog.openCount").value(0))
                .andExpect(jsonPath("$.data.backlog.oldestWaitMinutes").doesNotExist())
                .andExpect(jsonPath("$.data.backlog.openByLatestJudgment").isEmpty())
                .andExpect(jsonPath("$.data.alarms[?(@.code == 'BACKLOG_AGE')]").doesNotExist());
    }

    /** 큐가 비어 있으면 임계를 넘지 않고 경보도 없다. */
    @Test
    void 큐가_비면_적체_지표가_0이고_경보가_없다() throws Exception {
        modelClear(LocalDateTime.now());

        mockMvc.perform(bearer(get(BASE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.backlog.openCount").value(0))
                .andExpect(jsonPath("$.data.backlog.oldestWaitMinutes").doesNotExist())
                .andExpect(jsonPath("$.data.backlog.breached").value(false))
                .andExpect(jsonPath("$.data.needsAttention").value(false))
                .andExpect(jsonPath("$.data.alarms").isEmpty());
    }

    /**
     * 창 밖의 것은 세지 않는다. 적체만 창을 보지 않는다.
     *
     * <p>창이 없으면 지표가 누적 평균이 되어, 선별을 켠 뒤의 변화가 켜기 전 데이터에 묻힌다.
     * 반대로 적체에 창을 걸면 창보다 오래 열려 있는 건이 빠지는데, 그게 제일 먼저 봐야 하는 건이다.
     */
    @Test
    void 집계_창_밖의_호출과_검수는_세지_않지만_적체는_창을_보지_않는다() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        long old = verdictFailed(now.minusDays(3));
        triageCall(old, "SUCCESS", 900);
        backdateLedger(old, now.minusDays(3));
        backdateQueueRow(openQueueRow(now.minusDays(3)), now.minusDays(3));

        mockMvc.perform(bearer(get(BASE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.coverage.settledCount").value(0))
                .andExpect(jsonPath("$.data.funnel.settledCount").value(0))
                .andExpect(jsonPath("$.data.funnel.stage2.enteredCount").value(0))
                .andExpect(jsonPath("$.data.cost.callCount").value(0))
                .andExpect(jsonPath("$.data.backlog.openCount").value(1))
                // 상품 검수가 창 밖이라도 최신 판정은 읽는다. 적체는 창을 보지 않기 때문이다.
                .andExpect(jsonPath("$.data.backlog.openByLatestJudgment.NO_VERDICT")
                        .value(1))
                .andExpect(jsonPath("$.data.backlog.breached").value(true));
    }

    /**
     * 원장을 검수가 닫힌 시각에 앵커한다.
     *
     * <p>앵커를 섞어 두면(비용은 호출 시각, 누락률은 검수 시각) 창 경계에 걸친 건이 지표마다
     * 다른 버킷에 들어간다. 이 테스트는 그 어긋남을 직접 만든다 — 호출은 창 밖에 적혔고 검수는
     * 창 안에 닫혔다. 비용은 검수를 따라와야 한다.
     */
    @Test
    void 호출_시각이_창_밖이어도_검수가_창_안에_닫혔으면_비용에_들어온다() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        long inspection = modelClear(now);
        modelCall(inspection, InspectionPerspectiveKind.DISPLAY_ADS, 3_800);
        backdateLedger(inspection, now.minusDays(3));

        mockMvc.perform(bearer(get(BASE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cost.callCount").value(1))
                .andExpect(jsonPath("$.data.cost.totalCostMicroUsd").value(3_800))
                .andExpect(
                        jsonPath("$.data.cost.costPerSettledInspectionMicroUsd").value(3_800));
    }

    /**
     * 폭이 0인 창은 거부한다.
     *
     * <p>모든 분모가 0이 되어 화면이 "미측정"으로 채워지는데, 그것이 데이터가 없다는 뜻인지 창을
     * 잘못 줬다는 뜻인지 구분되지 않는다. 상한도 같은 코드로 거부한다 — 창이 옵셔널이라 상한이
     * 없으면 10년 창 요청 하나로 전수 스캔이 된다.
     */
    @Test
    void 폭이_0이거나_상한을_넘는_창은_거부한다() throws Exception {
        LocalDateTime now = LocalDateTime.now().withNano(0);

        mockMvc.perform(bearer(get(BASE).param("from", now.toString()).param("to", now.toString())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(40506));

        mockMvc.perform(bearer(
                        get(BASE).param("from", now.minusDays(200).toString()).param("to", now.toString())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(40506));

        // 상한을 일 단위로 절삭해 비교하면 여기가 통과한다 — 92일 23시간 59분이 92로 계산되어
        // 실효 상한이 93일에 1초 못 미치는 값이 되고, 상한을 둔 이유가 하루치 느슨해진다.
        mockMvc.perform(bearer(get(BASE)
                        .param(
                                "from",
                                now.minusDays(92)
                                        .minusHours(23)
                                        .minusMinutes(59)
                                        .toString())
                        .param("to", now.toString())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(40506));
    }

    /**
     * 창 길이를 분으로도 싣는다.
     *
     * <p>시간은 절삭이라 한 시간보다 짧은 창이 0으로 나간다. 그 값만 보면 30분 창과 창이 없는
     * 상태가 같은 글자가 되고, 주기 로그도 "창=0시간"으로 찍힌다.
     */
    @Test
    void 한_시간보다_짧은_창도_길이를_읽을_수_있다() throws Exception {
        LocalDateTime now = LocalDateTime.now().withNano(0);

        mockMvc.perform(bearer(get(BASE)
                        .param("from", now.minusMinutes(30).toString())
                        .param("to", now.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.window.windowHours").value(0))
                .andExpect(jsonPath("$.data.window.windowMinutes").value(30));
    }

    /**
     * 정책 버전 축으로 좁히면 그 버전만 센다.
     *
     * <p>정책 버전 리터럴이 백엔드 설정과 AI 정책 문구 두 곳에 손으로 적혀 있어 어긋날 수 있다.
     * 한 버전 버킷에 프롬프트 버전이 둘 이상이면 그것이 어긋남이고, 그래서 프롬프트 버전 분포를
     * 창 블록에 함께 싣는다.
     */
    @Test
    void 정책_버전_축으로_좁히면_그_버전만_세고_프롬프트_버전을_함께_준다() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        modelClear(now, "policy-v1");
        modelClear(now, "policy-v2");
        modelClear(now, "policy-v2");

        mockMvc.perform(bearer(get(BASE).param("policyVersion", "policy-v2")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.window.policyVersion").value("policy-v2"))
                .andExpect(jsonPath("$.data.funnel.settledCount").value(2))
                .andExpect(jsonPath("$.data.window.promptVersions['inspection-v1+policy-v2+triage-v2']")
                        .value(2))
                .andExpect(jsonPath("$.data.window.promptVersions['inspection-v1+policy-v1+triage-v2']")
                        .doesNotExist());
    }
}
