package com.aechak.application.inspection.port.view;

/**
 * 한 단계의 지출 — 호출 수, 합계, 캐시를 못 쓴 호출 수.
 *
 * <p>단계는 호출 원장의 {@code perspective}다. 선별·표시광고·행위거래로 갈리고, {@code ALL}로
 * 적힌 행은 {@code UNATTRIBUTED}로 모인다 — 합계에서 빼지 않는다. 지금 그 값은 셋이 섞인
 * 버킷이다: 관점 팬아웃을 끈 단일 판정 호출, 비평 호출, 수정 문구 호출. AI 쪽 관점 열거에
 * 비평·수정 문구가 없어서 셋이 같은 값으로 적히고, {@code purpose}로도 복원되지 않는다(그
 * 칼럼은 대상 종류에서만 만들어져 상품·리뷰 둘뿐이다). 그래서 지금은 단계를 복원할 수 없고,
 * 그 사실을 숨기면 단계별 합이 총액에 못 미치는 이유를 알 수 없다.
 *
 * <p>캐시 미적중을 단계별로 쪼개는 이유가 있다. 선별 프롬프트는 Haiku 최소 캐시 길이에 못 미쳐
 * 0이 아닌 것이 정상이고, 관점 호출은 0이 아니면 접두부가 흔들렸다는 사건이다. 한 수치로
 * 뭉치면 둘을 가를 수 없다.
 */
public record InspectionStageCost(long callCount, long costMicroUsd, long cacheMissCallCount) {}
