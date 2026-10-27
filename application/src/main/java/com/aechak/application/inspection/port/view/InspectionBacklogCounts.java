package com.aechak.application.inspection.port.view;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 관리자 대기 적체의 원재료.
 *
 * <p><b>창을 보지 않는다.</b> 적체는 비율이 아니라 지금 열려 있는 큐의 모양이고, 24시간보다
 * 오래 열려 있는 건이 빠지면 가장 먼저 봐야 하는 건이 지표에서 사라진다.
 *
 * <p>{@code openCount}는 <b>관리자 큐 목록과 같은 필터</b>로 센다. 목록이 상품·셀러를 안쪽
 * 조인하므로 그 조인 없이 세면 지표는 "대기 50건"이라 말하는데 화면에는 3건만 보이는 상태가
 * 된다. 사람이 처리할 수 있는 건수가 적체의 정의다.
 *
 * <p><b>큐에 닿는 판정은 둘뿐이다.</b> 위반 판정과, 관리자가 반려한 상품에 온 통과 판정이다.
 * 판정 실패·기한 만료·모델이 내용을 읽은 적 없는 통과는 상품도 큐도 건드리지 않는다 — 모델이
 * 답을 주지 못한 것으로 노출을 끊으면 모델 장애가 곧 매출 장애가 되고, 사람이 풀 것도 확정할
 * 것도 없는 행을 쌓으면 장애마다 운영자가 수백 건을 떠안는다.
 *
 * <p>그래서 <b>갱신도 닫힘도 없이 묵는 열린 행이 생길 수 있다.</b> 위반 판정으로 열린 행에
 * 셀러가 내용을 고쳐 재검수가 돌았는데 그 판정이 실패하거나 기한을 넘기면, 그 행은 그대로
 * 열린 채 남는다. 그 모양을 따로 세지는 않는다 — 열린 행은 어느 쪽이든 사람이 처리해야 하는
 * 건이고, 얼마나 오래 묵었는지는 {@code oldestWaitingSince}가 든다.
 *
 * <p><b>{@code oldestWaitingSince}는 행이 열린 시각(유입)이다.</b> 어드민 목록의 정렬 키인
 * {@code blinded_at}이 아니다 — 그 칼럼은 열린 행에 새 판정이 올 때마다 덮이므로, 관리자가 한
 * 번도 보지 않은 행이 매일 되감겨 영원히 "방금부터 기다림"으로 보고된다. 시간 임계가 존재하는
 * 이유가 바로 그 행이라 그것으로 재면 경보가 구조적으로 발화하지 않는다.
 *
 * <p>{@code openByLatestJudgment}는 <b>그 행을 연 판정이 아니라 그 상품의 가장 최근 판정</b>으로
 * 센다. 등재 사유 칼럼이 비어 있어(채우는 경로가 없다) 조회가 상품별 최신 검수를 붙이기 때문이고,
 * 그래서 행과 버킷의 판정이 서로 다른 검수일 수 있다. 판정을 아직 기다리는 행의 키는
 * {@code NO_VERDICT}다.
 *
 * <p>버킷마다 담기는 것은 이렇다.
 *
 * <ul>
 *   <li>{@code FLAG} — 아무도 다시 보지 않은 AI 반려.
 *   <li>{@code PASS} — 둘이 섞인다. 관리자가 반려한 상품의 재검수 통과(사람이 볼 차례)와, AI
 *       반려로 열린 행에 모델이 읽은 적 없는 통과가 와서 게이트가 아무것도 하지 않은 것(계약
 *       드리프트)이다. 저장된 판정이 통과이고 그것이 최신이라 같은 칸에 든다.
 *   <li>{@code UNJUDGED}·{@code VERDICT_FAILED} — 위반으로 열린 행인데 재검수가 죽거나 기한을
 *       넘긴 것. 구조적으로 0이 아니다.
 *   <li>{@code NO_VERDICT} — 재검수가 아직 진행 중.
 * </ul>
 *
 * <p>섞인 {@code PASS}를 가르려면 "지금 이 행을 막고 있는 판정"을 행에 적는 칼럼이 필요해서
 * 쿼리로는 가르지 않는다. 대신 함께 읽을 신호가 있다 — {@code InspectionCoverageCounts}의
 * {@code modelUnverifiedCount}다. {@code PASS} 버킷이 늘고 그 값도 같이 늘면 드리프트 쪽이고,
 * 그 값이 0이면 사람이 볼 차례 쪽이다.
 */
public record InspectionBacklogCounts(
        long openCount, LocalDateTime oldestWaitingSince, Map<String, Long> openByLatestJudgment) {

    /** 가장 최근 판정이 없는 열린 행의 키. 셀러가 고친 내용을 AI가 아직 보는 중인 행이다 */
    public static final String NO_VERDICT = "NO_VERDICT";

    public static InspectionBacklogCounts empty() {
        return new InspectionBacklogCounts(0, null, Map.of());
    }
}
