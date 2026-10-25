package com.aechak.application.inspection.usecase.result;

import com.aechak.application.inspection.port.InspectionMetricsWindow;
import com.aechak.application.inspection.port.view.InspectionBacklogCounts;
import com.aechak.application.inspection.port.view.InspectionCostCounts;
import com.aechak.application.inspection.port.view.InspectionCoverageCounts;
import com.aechak.application.inspection.port.view.InspectionFunnelCounts;
import com.aechak.application.inspection.port.view.InspectionStageCost;
import com.aechak.application.inspection.port.view.InspectionTriageCounts;
import com.aechak.domain.inspection.enums.InspectionTargetType;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 운영 지표 한 장 — 퍼널·판정·누락·비용·적체.
 *
 * <p>비율과 임계 판정을 전부 이 레코드가 들고 있다. 어드민 화면과 주기 로그가 같은 수치를
 * 보려면 계산이 한 곳에 있어야 하고, 임계 초과 판정을 호출부마다 다시 쓰면 화면에는 경보가
 * 뜨는데 로그는 조용한 상태가 생긴다. 경보 문장도 여기서 만든다 — 로그에만 있으면 화면과
 * 로그가 다른 말을 한다.
 *
 * <p><b>분모가 0일 때 비율은 {@code null}이다.</b> 0으로 내려보내면 "선별이 한 건도 안 돌았다"와
 * "돌았는데 전부 건너뛰었다"가 같은 숫자가 된다 — 켤지 말지를 그 숫자로 판단하면 안 된다.
 * 지금은 AI 쪽 선별 기본값이 꺼짐이라 승급률이 미측정으로 나오는 것이 정상이다.
 *
 * <p>금액은 백만분의 1 USD로만 둔다. 원화 환산은 환율 전제를 섞는 일이라 보는 쪽에서 한다.
 *
 * <p>검수 사유 원문과 위반 상세는 어디에도 싣지 않는다. 리뷰 검수의 같은 칼럼에는 리뷰 작성자가
 * 쓴 문장이 들어가므로 유출 경로를 만들면 안 된다. 수치만 싣는 규약을 지키면 그 자리가 없다.
 */
public record InspectionMetricsResult(
        Window window, List<Alarm> alarms, Funnel funnel, Judgment judgment, Coverage coverage, Cost cost, Backlog backlog) {

    /**
     * 집계 창과 쪼갠 축. 프롬프트 버전 분포가 여기 붙는 이유는 축이 어긋났는지를 드러내려는 것이다.
     *
     * <p>{@code windowHours}는 절삭이라 한 시간보다 짧은 창에서 0이 된다. {@code windowMinutes}를
     * 함께 싣는다 — 시간만 보면 30분 창과 창이 없는 상태가 같은 글자가 된다.
     */
    public record Window(
            LocalDateTime from,
            LocalDateTime to,
            LocalDateTime measuredAt,
            int windowHours,
            long windowMinutes,
            String policyVersion,
            InspectionTargetType targetType,
            Map<String, Long> promptVersions) {}

    /** 경보 한 줄. 코드는 화면이 분기할 축이고 문장은 사람이 읽을 이유다 */
    public record Alarm(String code, String message) {}

    /**
     * 탈출 한 칸.
     *
     * <p>{@code stage}가 0인 것은 퍼널 밖(실패 터미널)이다 — 세로 퍼널 아래 별도 칸으로 그린다.
     * 순서는 고정이다. 화면이 재정렬하면 단계 순서가 응답마다 달라진다.
     */
    public record Exit(int stage, String exit, String label, long count, Double shareOfSettled) {}

    /**
     * 단계별 탈출 분포.
     *
     * <p>{@code unclassifiedCount}는 탈출 목록에 넣지 않는다. 0이 정상이고 0이 아니면 새 터미널이
     * 생겼거나 분류 규칙이 실제 기록과 어긋났다는 뜻이라, 퍼널의 한 칸이 아니라 사건이다.
     *
     * <p>{@code expiredMarkerCount}는 기한 정리가 남긴 미검사 표식이 있는 검수 수다.
     * {@code stage5.unjudgedCount}와 같아야 한다 — 어긋나면 경보로 올라간다.
     */
    public record Funnel(
            long settledCount,
            long unclassifiedCount,
            long expiredMarkerCount,
            long missingSkipReasonCount,
            List<Exit> exits,
            Stage1 stage1,
            Stage2 stage2,
            Stage3 stage3,
            Stage4 stage4,
            Stage5 stage5) {}

    /**
     * 1단 — 결정론적 규칙.
     *
     * <p>분모에서 미판정을 뺀다. 그 건들은 AI 응답을 받은 적이 없어 규칙 단계를 탄 적도 없다 —
     * 기한 정리가 미검사 행 하나만 적고 닫는다. 넣으면 모델 장애가 룰 차단율을 끌어내린다.
     */
    public record Stage1(String name, long enteredCount, long blockedCount, Double blockRate) {}

    /**
     * 2단 — 선별. 1순위 지표가 여기 있다.
     *
     * <p>분모는 선별 <b>호출</b>이 나간 검수다. 규칙이 적중해 선별에 묻지도 않고 승급한 건은
     * {@code bypassedByRuleHitCount}로 따로 센다 — 그 수치가 없으면 1단 통과 건수와 2단 분모
     * 사이에서 건수가 사라진 것처럼 보인다.
     *
     * <p>손익분기 승급률의 정본은 AI 레포의 측정 하네스다. {@code overBreakeven}이 참이면 선별이
     * 비용을 줄이는 장치가 아니라 더하는 장치라는 뜻이고, 그때는 선별을 켜지 않는다.
     */
    public record Stage2(
            String name,
            long enteredCount,
            long clearedCount,
            long promotedCount,
            long callFailedCount,
            long bypassedByRuleHitCount,
            Double skipRate,
            Double promotionRate,
            double breakevenRate,
            boolean overBreakeven) {}

    /**
     * 3단 — 관점 판정.
     *
     * <p>검출율의 분모를 "3단을 탄 건"이 아니라 "응답이 온 건"으로 잡는다. 레이트 리밋으로 못 본
     * 건을 분모에 넣으면 검출율이 모델 품질이 아니라 가용성을 재게 된다. 그 차이는
     * {@code enteredCount - respondedCount}로 보인다. {@code enteredCount}에는 호출이 실제로
     * 나간 건만 들어온다 — 선별이 건너뛴 건과 스텁 모드 건은 모델 단계에 들어간 적이 없다.
     */
    public record Stage3(
            String name,
            long enteredCount,
            long respondedCount,
            long violationFoundCount,
            long clearCount,
            Double detectionRate) {}

    /**
     * 4단 — 비평.
     *
     * <p>{@code escalationObserved}가 항상 {@code null}이다 — <b>지금은 측정 수단이 없다</b>.
     * 비평 호출이 원장에 전용 관점으로 적히지 않고 단일 판정·수정 문구와 같은 {@code ALL} 값을
     * 쓰기 때문에, 비평이 실제로 돌았는지를 가를 수 없다. 그래서 이 비율은 조건부로 틀린다.
     * 비평 단계가 꺼져 있으면 모델 위반 건이 분모에 그대로 들어오고 분자는 0이 되어 "비평이
     * 쓸모없다"는 반대 결론이 나온다. 주기 로그가 이 비율로 경보하지 않는 이유다.
     *
     * <p>푸는 순서는 정해져 있다. 백엔드 열거에 비평·수정 문구 관점을 먼저 더해 배포하고 그
     * 다음 AI를 배포한다 — 모르는 관점 값이 먼저 오면 역직렬화에서 떨어진다.
     */
    public record Stage4(
            String name,
            long enteredCount,
            long overturnedCount,
            long upheldCount,
            Double overturnRate,
            Boolean escalationObserved) {}

    /** 5단 — 최종. 탈출 분포와 합이 맞는지 눈으로 대조하는 칸이다 */
    public record Stage5(String name, long flagCount, long passCount, long failedCount, long unjudgedCount) {}

    /** 최종 판정 분포. 퍼널과 교차 검증하는 수치다 — PASS = 선별통과 + 위반없음 + 뒤집힘이어야 한다 */
    public record Judgment(
            long settledCount, long passCount, long flagCount, long unjudgedCount, long failedCount, Double passRate) {}

    /** 누락률 — 아무도 내용을 보지 않은 채 닫힌 비율. */
    public record Coverage(
            long settledCount,
            long unverifiedCount,
            long unjudgedCount,
            long verdictFailedCount,
            long modelUnverifiedCount,
            long triageSkippedPassCount,
            Double unverifiedRate) {

        static Coverage of(InspectionCoverageCounts counts) {
            long unverified = counts.unjudgedCount() + counts.verdictFailedCount() + counts.modelUnverifiedCount();
            return new Coverage(
                    counts.settledCount(),
                    unverified,
                    counts.unjudgedCount(),
                    counts.verdictFailedCount(),
                    counts.modelUnverifiedCount(),
                    counts.triageSkippedPassCount(),
                    ratio(unverified, counts.settledCount()));
        }
    }

    /**
     * 관리자 대기 적체.
     *
     * <p>임계를 둘 둔다. 쌓인 건수와 가장 오래 기다린 시간이다. 건수만 보면 하루 한 건씩
     * 들어와 아무도 보지 않는 큐가 임계에 닿지 않고, 시간만 보면 사람이 오래된 것부터 치우는
     * 동안 뒤에 쌓이는 양이 안 보인다.
     */
    public record Backlog(
            long openCount,
            LocalDateTime oldestWaitingSince,
            Long oldestWaitMinutes,
            Map<String, Long> openByLatestJudgment,
            long warnCount,
            long warnAgeMinutes) {

        static Backlog of(InspectionBacklogCounts counts, LocalDateTime now, long warnCount, long warnAgeMinutes) {
            Long waitMinutes = counts.oldestWaitingSince() == null
                    ? null
                    : Math.max(
                            0,
                            Duration.between(counts.oldestWaitingSince(), now).toMinutes());
            return new Backlog(
                    counts.openCount(),
                    counts.oldestWaitingSince(),
                    waitMinutes,
                    counts.openByLatestJudgment(),
                    warnCount,
                    warnAgeMinutes);
        }

        /** 임계를 넘었는가. 넘으면 "사람 검수 단계가 있다"는 말이 사실이 아니게 된다 */
        public boolean breached() {
            return countBreached() || ageBreached();
        }

        public boolean countBreached() {
            return openCount >= warnCount;
        }

        public boolean ageBreached() {
            return oldestWaitMinutes != null && oldestWaitMinutes >= warnAgeMinutes;
        }
    }

    /**
     * 비용 — 창 안에 닫힌 검수에 붙은 지출.
     *
     * <p>건당 비용을 둘 내려보낸다. {@code costPerInspectionMicroUsd}는 <b>호출이 나간 건당</b>이고
     * 분모가 원장 유래라 규칙이 무료로 차단한 건과 스텁 건이 빠진다 — 1단 차단율이 10%면 건당
     * 비용이 11% 비싸게 보인다. {@code costPerSettledInspectionMicroUsd}가 창 안에 닫힌 검수
     * 전수를 분모로 한 진짜 건당이다. 선별을 켤지를 이 숫자로 판단하므로 둘을 함께 둔다.
     */
    public record Cost(
            long callCount,
            long inspectionCount,
            long totalCostMicroUsd,
            long triageCostMicroUsd,
            long cacheMissCallCount,
            Long costPerInspectionMicroUsd,
            Long costPerSettledInspectionMicroUsd,
            Map<String, StageCost> byStage) {

        static Cost of(InspectionCostCounts counts, Map<String, InspectionStageCost> stages, long settledCount) {
            Map<String, StageCost> byStage = new LinkedHashMap<>();
            for (Map.Entry<String, InspectionStageCost> entry : stages.entrySet()) {
                InspectionStageCost stage = entry.getValue();
                byStage.put(
                        entry.getKey(),
                        new StageCost(
                                stage.callCount(),
                                stage.costMicroUsd(),
                                stage.cacheMissCallCount(),
                                perUnit(stage.costMicroUsd(), settledCount)));
            }
            return new Cost(
                    counts.callCount(),
                    counts.inspectionCount(),
                    counts.totalCostMicroUsd(),
                    counts.triageCostMicroUsd(),
                    counts.cacheMissCallCount(),
                    perUnit(counts.totalCostMicroUsd(), counts.inspectionCount()),
                    perUnit(counts.totalCostMicroUsd(), settledCount),
                    byStage);
        }
    }

    /** 한 단계의 지출. {@code UNATTRIBUTED}는 단계를 복원할 수 없는 행이고 합계에서 빼지 않는다 */
    public record StageCost(long callCount, long costMicroUsd, long cacheMissCallCount, Long costPerSettledMicroUsd) {}

    @SuppressWarnings("checkstyle:ParameterNumber")
    public static InspectionMetricsResult of(
            InspectionMetricsWindow window,
            LocalDateTime measuredAt,
            Map<String, Long> promptVersions,
            double breakevenRate,
            long backlogWarnCount,
            long backlogWarnAgeMinutes,
            InspectionFunnelCounts funnel,
            InspectionTriageCounts triage,
            InspectionCoverageCounts coverage,
            InspectionCostCounts cost,
            Map<String, InspectionStageCost> stageCosts,
            InspectionBacklogCounts backlog) {
        long settled = funnel.settledCount();
        InspectionMetricsResult result = new InspectionMetricsResult(
                new Window(
                        window.from(),
                        window.to(),
                        measuredAt,
                        window.hours(),
                        window.minutes(),
                        window.policyVersion(),
                        window.targetType(),
                        promptVersions),
                List.of(),
                funnelOf(funnel, triage, breakevenRate),
                judgmentOf(funnel),
                Coverage.of(coverage),
                Cost.of(cost, stageCosts, settled),
                Backlog.of(backlog, measuredAt, backlogWarnCount, backlogWarnAgeMinutes));

        // 경보는 다른 블록의 값에서 나오므로 블록을 만든 뒤에 붙인다. 레코드가 불변이라
        // 같은 값으로 한 번 다시 조립하는 쪽이 세터를 두는 것보다 안전하다.
        return new InspectionMetricsResult(
                result.window(),
                result.buildAlarms(),
                result.funnel(),
                result.judgment(),
                result.coverage(),
                result.cost(),
                result.backlog());
    }

    /** 사람이 바로 봐야 하는 상태인가. 주기 로그의 단계를 이 한 줄이 정한다 */
    public boolean needsAttention() {
        return !alarms.isEmpty();
    }

    /** 경보를 한 줄로 이어 붙인다. 로그가 쓰는 모양이고 화면의 배너와 같은 문장이다 */
    public String alarmLine() {
        return String.join("; ", alarms.stream().map(Alarm::message).toList());
    }

    private List<Alarm> buildAlarms() {
        List<Alarm> found = new ArrayList<>();
        if (backlog.countBreached()) {
            found.add(new Alarm(
                    "BACKLOG_COUNT",
                    "관리자 대기 %d건이 임계 %d건을 넘었다".formatted(backlog.openCount(), backlog.warnCount())));
        }
        if (backlog.ageBreached()) {
            found.add(new Alarm(
                    "BACKLOG_AGE",
                    "가장 오래된 대기가 %d분으로 임계 %d분을 넘었다"
                            .formatted(backlog.oldestWaitMinutes(), backlog.warnAgeMinutes())));
        }
        if (funnel.stage2().overBreakeven()) {
            found.add(new Alarm("OVER_BREAKEVEN", "승급률이 손익분기를 넘었다 — 선별이 비용을 줄이지 못하는 구간이다"));
        }
        if (funnel.unclassifiedCount() > 0) {
            found.add(new Alarm(
                    "UNCLASSIFIED_EXIT",
                    "어느 탈출로도 분류되지 않은 검수가 %d건이다 — 새 터미널이 생겼거나 분류 규칙이 어긋났다"
                            .formatted(funnel.unclassifiedCount())));
        }
        // 만료 건을 표식으로 명시 제외하는 규약의 전제를 운영 중에 감시한다. 어긋나면 다른
        // 경로가 미판정을 만들었거나 기한 스윕이 검수 행만 닫고 관점 행을 못 적은 것이다.
        if (funnel.expiredMarkerCount() != funnel.stage5().unjudgedCount()) {
            found.add(new Alarm(
                    "EXPIRED_MARKER_MISMATCH",
                    "미판정 %d건인데 기한 만료 표식은 %d건이다 — 기한 정리가 관점 행을 못 적었거나 다른 경로가 미판정을 만들었다"
                            .formatted(funnel.stage5().unjudgedCount(), funnel.expiredMarkerCount())));
        }
        if (funnel.missingSkipReasonCount() > 0) {
            found.add(new Alarm(
                    "MISSING_SKIP_REASON",
                    "사유 없는 미검사 기록이 %d건이다 — 선별이 건너뛴 것과 호출이 죽은 것을 가를 수 없다"
                            .formatted(funnel.missingSkipReasonCount())));
        }
        return List.copyOf(found);
    }

    private static Funnel funnelOf(InspectionFunnelCounts counts, InspectionTriageCounts triage, double breakevenRate) {
        long settled = counts.settledCount();
        // 1단 분모에서 미판정을 뺀다 — 그 건은 AI 응답을 받은 적이 없어 규칙 단계를 탄 적도 없다.
        long stage1Entered = settled - counts.unjudgedCount();
        long promoted = triage.triagedCount() - triage.clearedCount();
        long responded = counts.modelViolationCount() + counts.modelClearCount();
        long upheld = counts.modelViolationCount() - counts.criticOverturnedCount();

        List<Exit> exits = List.of(
                exit(1, "RULE_BLOCKED", "룰 확정 차단", counts.ruleBlockedCount(), settled),
                exit(2, "TRIAGE_CLEARED", "선별 통과", counts.triageClearedCount(), settled),
                exit(3, "MODEL_CLEAR", "위반 없음", counts.modelClearExitCount(), settled),
                exit(4, "CRITIC_OVERTURNED", "비평이 뒤집음", counts.criticOverturnedCount(), settled),
                exit(5, "MODEL_FLAG", "확정 위반", counts.modelFlagCount(), settled),
                exit(0, "VERDICT_FAILED", "판정 실패", counts.verdictFailedExitCount(), settled),
                exit(0, "UNJUDGED", "미판정", counts.unjudgedExitCount(), settled));

        return new Funnel(
                settled,
                counts.unclassifiedCount(),
                counts.expiredMarkerCount(),
                counts.missingSkipReasonCount(),
                exits,
                new Stage1("RULE", stage1Entered, counts.ruleBlockedCount(), ratio(counts.ruleBlockedCount(), stage1Entered)),
                new Stage2(
                        "TRIAGE",
                        triage.triagedCount(),
                        triage.clearedCount(),
                        promoted,
                        triage.callFailedCount(),
                        counts.ruleHitBypassCount(),
                        ratio(triage.clearedCount(), triage.triagedCount()),
                        ratio(promoted, triage.triagedCount()),
                        breakevenRate,
                        overBreakeven(promoted, triage.triagedCount(), breakevenRate)),
                new Stage3(
                        "JUDGE",
                        counts.modelDispatchedCount(),
                        responded,
                        counts.modelViolationCount(),
                        counts.modelClearCount(),
                        ratio(counts.modelViolationCount(), responded)),
                new Stage4(
                        "CRITIC",
                        counts.modelViolationCount(),
                        counts.criticOverturnedCount(),
                        upheld,
                        ratio(counts.criticOverturnedCount(), counts.modelViolationCount()),
                        null),
                new Stage5("FINAL", counts.flagCount(), counts.passCount(), counts.failedCount(), counts.unjudgedCount()));
    }

    private static Judgment judgmentOf(InspectionFunnelCounts counts) {
        return new Judgment(
                counts.settledCount(),
                counts.passCount(),
                counts.flagCount(),
                counts.unjudgedCount(),
                counts.failedCount(),
                ratio(counts.passCount(), counts.settledCount()));
    }

    private static Exit exit(int stage, String code, String label, long count, long settled) {
        return new Exit(stage, code, label, count, ratio(count, settled));
    }

    /** 손익분기를 넘었는가. 분모가 0이면 비율이 없으므로 경보도 없다 */
    private static boolean overBreakeven(long promoted, long triaged, double breakevenRate) {
        Double rate = ratio(promoted, triaged);
        return rate != null && rate > breakevenRate;
    }

    /** 분모가 0이면 비율이 없다. 0으로 바꾸면 "안 돌았다"와 "전부 0이었다"가 같은 숫자가 된다 */
    private static Double ratio(long numerator, long denominator) {
        return denominator == 0 ? null : (double) numerator / denominator;
    }

    /** 건당 금액. 분모가 0이면 금액이 없다 */
    private static Long perUnit(long total, long denominator) {
        return denominator == 0 ? null : total / denominator;
    }
}
