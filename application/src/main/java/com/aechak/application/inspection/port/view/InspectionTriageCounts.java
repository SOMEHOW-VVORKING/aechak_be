package com.aechak.application.inspection.port.view;

/**
 * 승급률(1순위 지표)의 원재료.
 *
 * <p>분모는 <b>선별 호출이 실제로 나간 검수</b>다. 규칙이 적중해 선별에 묻지도 않고 바로
 * 승급한 건은 호출 원장에 선별 행이 없어 여기에 들어오지 않는다 — 그 건을 분모에 넣으면
 * "선별 호출 한 번으로 관점 호출을 아꼈는가"라는 질문의 분모가 아니게 된다. 그래서 관점
 * 기록이 아니라 원장으로 센다. 기록으로 세면 규칙 우회 건에도 선별 행이 남아 있어
 * (UNCHECKED/RULE_HIT) 호출하지도 않은 건이 분모에 들어와 승급률이 낮게 나온다. AI 레포의
 * 측정 하네스도 같은 분모를 쓴다.
 *
 * <p>승급 건수는 분모에서 {@code clearedCount}를 뺀 값이다. 건너뛴 건은 관점 행이
 * {@code CLEAR}인 것으로 가른다 — 행의 유무로 가르면 승급 경로에도 선별 행이 남으므로
 * (UNCHECKED/PROMOTED) 승급 전건이 건너뜀으로 분류되고 1순위 지표가 뒤집힌다.
 *
 * <p>선별 호출이 깨져 강제 승급된 건도 승급으로 센다 — 관점 호출이 실제로 나갔으므로 비용
 * 관점에서는 같다. 다만 그 건수를 {@code callFailedCount}로 따로 들고 있어야 "선별이 판단해서
 * 올린 것"과 "죽어서 올라간 것"을 가를 수 있다.
 */
public record InspectionTriageCounts(long triagedCount, long clearedCount, long callFailedCount) {

    public static InspectionTriageCounts empty() {
        return new InspectionTriageCounts(0, 0, 0);
    }
}
