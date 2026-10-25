package com.aechak.admin.inspection.response;

import com.aechak.application.inspection.usecase.result.InspectionMetricsResult;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 검수 운영 지표 한 장.
 *
 * <p>비율은 0~1 실수 그대로 내려보낸다. 분모가 0이면 {@code null}이다 — 0으로 바꾸면 "한 건도
 * 안 돌았다"와 "돌았는데 전부 건너뛰었다"가 같은 숫자가 되고, 선별을 켤지 말지를 그 숫자로
 * 판단하면 안 된다. 보는 쪽은 {@code null}을 "미측정"으로 쓴다.
 *
 * <p>금액은 백만분의 1 USD로만 내려보낸다. 원화 환산은 환율 전제를 섞는 일이라 보는 쪽에서 한다.
 *
 * <p>{@code alarms}는 주기 로그가 올리는 문장과 같은 값이다. 화면과 로그가 갈라지지 않게 판정과
 * 문장을 유스케이스가 한 번만 만든다. {@code needsAttention}은 그 목록이 비었는지의 요약이다.
 *
 * <p>{@code funnel.exits}는 순서가 고정이고 {@code stage}가 0인 칸은 퍼널 밖(실패 터미널)이다.
 * {@code funnel.unclassifiedCount}가 0이 아니면 그 자체가 사건이다 — 새 터미널이 생겼거나 분류
 * 규칙이 실제 기록과 어긋났다는 뜻이다.
 *
 * <p>{@code funnel.stage4.escalationObserved}는 항상 {@code null}이다. 비평 호출이 원장에 전용
 * 관점으로 적히지 않아 실제로 돌았는지 가를 수 없다 — 4단 비율을 읽을 때 그 사실을 함께 본다.
 *
 * <p>{@code cost.byStage}의 {@code UNATTRIBUTED}는 단계를 복원할 수 없는 호출이다. 합계에서
 * 빼지 않는다 — 숨기면 단계별 합이 총액에 못 미치는 이유를 알 수 없다.
 *
 * <p>검수 사유 원문과 위반 상세는 싣지 않는다. 집계 수치만 내려보낸다.
 */
public record AdminInspectionMetricsResponse(
        Window window,
        boolean needsAttention,
        List<Alarm> alarms,
        Funnel funnel,
        Judgment judgment,
        Coverage coverage,
        Cost cost,
        Backlog backlog) {

    /** {@code windowHours}는 절삭이라 한 시간보다 짧은 창에서 0이다. 복원은 {@code windowMinutes}로 한다 */
    public record Window(
            LocalDateTime from,
            LocalDateTime to,
            LocalDateTime measuredAt,
            int windowHours,
            long windowMinutes,
            String policyVersion,
            String targetType,
            Map<String, Long> promptVersions) {}

    public record Alarm(String code, String message) {}

    public record Exit(int stage, String exit, String label, long count, Double shareOfSettled) {}

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

    public record Stage1(String name, long enteredCount, long blockedCount, Double blockRate) {}

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

    public record Stage3(
            String name,
            long enteredCount,
            long respondedCount,
            long violationFoundCount,
            long clearCount,
            Double detectionRate) {}

    public record Stage4(
            String name,
            long enteredCount,
            long overturnedCount,
            long upheldCount,
            Double overturnRate,
            Boolean escalationObserved) {}

    public record Stage5(String name, long flagCount, long passCount, long failedCount, long unjudgedCount) {}

    public record Judgment(
            long settledCount, long passCount, long flagCount, long unjudgedCount, long failedCount, Double passRate) {}

    /** 누락률 — 아무도 내용을 보지 않은 채 닫힌 비율. 선별이 놓친 비율의 정답은 백엔드에 없다 */
    public record Coverage(
            long settledCount,
            long unverifiedCount,
            long unjudgedCount,
            long verdictFailedCount,
            long modelUnverifiedCount,
            long triageSkippedPassCount,
            Double unverifiedRate) {}

    public record Cost(
            long callCount,
            long inspectionCount,
            long totalCostMicroUsd,
            long triageCostMicroUsd,
            long cacheMissCallCount,
            Long costPerInspectionMicroUsd,
            Long costPerSettledInspectionMicroUsd,
            Map<String, StageCost> byStage) {}

    public record StageCost(long callCount, long costMicroUsd, long cacheMissCallCount, Long costPerSettledMicroUsd) {}

    /**
     * 적체만 창을 보지 않는다 — 창보다 오래 열려 있는 건이 빠지면 제일 먼저 봐야 하는 건이 사라진다.
     *
     * <p>정책 버전 축도 보지 않는다. 큐 행에 정책 버전이 없어서인데, 그러면 축으로 좁힌 응답에서
     * 비율은 그 버전만 센 값이고 적체는 전체 큐라 수치의 뜻이 블록마다 다르다. 정책 버전 둘을
     * 번갈아 조회해 비교하는 관리자는 같은 적체를 두 버전 모두에 귀속시킨다. 그래서 그 사실을
     * 응답에 싣는다 — {@code windowScoped}·{@code policyScoped} 가 거짓이면 이 블록은 창·버전과
     * 무관한 "지금 열려 있는 큐"다. 대상 종류는 가린다(상품 큐라 리뷰 축에는 빈 값이 나간다).
     */
    public record Backlog(
            long openCount,
            LocalDateTime oldestWaitingSince,
            Long oldestWaitMinutes,
            Map<String, Long> openByLatestJudgment,
            long warnCount,
            long warnAgeMinutes,
            boolean breached,
            boolean windowScoped,
            boolean policyScoped) {}

    public static AdminInspectionMetricsResponse from(InspectionMetricsResult result) {
        return new AdminInspectionMetricsResponse(
                windowOf(result.window()),
                result.needsAttention(),
                result.alarms().stream()
                        .map(alarm -> new Alarm(alarm.code(), alarm.message()))
                        .toList(),
                funnelOf(result.funnel()),
                judgmentOf(result.judgment()),
                coverageOf(result.coverage()),
                costOf(result.cost()),
                backlogOf(result.backlog()));
    }

    static Window windowOf(InspectionMetricsResult.Window window) {
        return new Window(
                window.from(),
                window.to(),
                window.measuredAt(),
                window.windowHours(),
                window.windowMinutes(),
                window.policyVersion(),
                window.targetType() == null ? null : window.targetType().name(),
                window.promptVersions());
    }

    static Funnel funnelOf(InspectionMetricsResult.Funnel funnel) {
        InspectionMetricsResult.Stage2 stage2 = funnel.stage2();
        return new Funnel(
                funnel.settledCount(),
                funnel.unclassifiedCount(),
                funnel.expiredMarkerCount(),
                funnel.missingSkipReasonCount(),
                funnel.exits().stream()
                        .map(exit ->
                                new Exit(exit.stage(), exit.exit(), exit.label(), exit.count(), exit.shareOfSettled()))
                        .toList(),
                new Stage1(
                        funnel.stage1().name(),
                        funnel.stage1().enteredCount(),
                        funnel.stage1().blockedCount(),
                        funnel.stage1().blockRate()),
                new Stage2(
                        stage2.name(),
                        stage2.enteredCount(),
                        stage2.clearedCount(),
                        stage2.promotedCount(),
                        stage2.callFailedCount(),
                        stage2.bypassedByRuleHitCount(),
                        stage2.skipRate(),
                        stage2.promotionRate(),
                        stage2.breakevenRate(),
                        stage2.overBreakeven()),
                new Stage3(
                        funnel.stage3().name(),
                        funnel.stage3().enteredCount(),
                        funnel.stage3().respondedCount(),
                        funnel.stage3().violationFoundCount(),
                        funnel.stage3().clearCount(),
                        funnel.stage3().detectionRate()),
                new Stage4(
                        funnel.stage4().name(),
                        funnel.stage4().enteredCount(),
                        funnel.stage4().overturnedCount(),
                        funnel.stage4().upheldCount(),
                        funnel.stage4().overturnRate(),
                        funnel.stage4().escalationObserved()),
                new Stage5(
                        funnel.stage5().name(),
                        funnel.stage5().flagCount(),
                        funnel.stage5().passCount(),
                        funnel.stage5().failedCount(),
                        funnel.stage5().unjudgedCount()));
    }

    static Judgment judgmentOf(InspectionMetricsResult.Judgment judgment) {
        return new Judgment(
                judgment.settledCount(),
                judgment.passCount(),
                judgment.flagCount(),
                judgment.unjudgedCount(),
                judgment.failedCount(),
                judgment.passRate());
    }

    static Coverage coverageOf(InspectionMetricsResult.Coverage coverage) {
        return new Coverage(
                coverage.settledCount(),
                coverage.unverifiedCount(),
                coverage.unjudgedCount(),
                coverage.verdictFailedCount(),
                coverage.modelUnverifiedCount(),
                coverage.triageSkippedPassCount(),
                coverage.unverifiedRate());
    }

    static Cost costOf(InspectionMetricsResult.Cost cost) {
        Map<String, StageCost> byStage = new LinkedHashMap<>();
        cost.byStage()
                .forEach((stage, value) -> byStage.put(
                        stage,
                        new StageCost(
                                value.callCount(),
                                value.costMicroUsd(),
                                value.cacheMissCallCount(),
                                value.costPerSettledMicroUsd())));
        return new Cost(
                cost.callCount(),
                cost.inspectionCount(),
                cost.totalCostMicroUsd(),
                cost.triageCostMicroUsd(),
                cost.cacheMissCallCount(),
                cost.costPerInspectionMicroUsd(),
                cost.costPerSettledInspectionMicroUsd(),
                byStage);
    }

    static Backlog backlogOf(InspectionMetricsResult.Backlog backlog) {
        return new Backlog(
                backlog.openCount(),
                backlog.oldestWaitingSince(),
                backlog.oldestWaitMinutes(),
                backlog.openByLatestJudgment(),
                backlog.warnCount(),
                backlog.warnAgeMinutes(),
                backlog.breached(),
                // 집계가 창도 정책 버전도 보지 않는다. 그쪽을 보게 되는 날 이 둘도 함께 바뀐다.
                false,
                false);
    }
}
