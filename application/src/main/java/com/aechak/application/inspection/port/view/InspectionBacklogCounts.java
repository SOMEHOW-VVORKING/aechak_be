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
 * <p>목록에서 내려간 묵은 행을 따로 세지 않는다. 그럴 자리가 없어졌기 때문이다 — 새 판정이
 * 오면 큐 서비스가 열린 행을 갱신하거나 닫으므로, 목록 필터와 지표 필터가 같은 열린 상태
 * 하나다. 셀러가 내용을 고쳐도 행이 열린 채 목록에서만 사라지는 모양이 구조적으로 없다.
 *
 * <p><b>{@code oldestWaitingSince}는 행이 열린 시각(유입)이다.</b> 어드민 목록의 정렬 키인
 * {@code blinded_at}이 아니다 — 그 칼럼은 열린 행에 새 판정이 올 때마다 덮이므로, 관리자가 한
 * 번도 보지 않은 행이 매일 되감겨 영원히 "방금부터 기다림"으로 보고된다. 시간 임계가 존재하는
 * 이유가 바로 그 행이라 그것으로 재면 경보가 구조적으로 발화하지 않는다.
 *
 * <p>{@code openByLatestJudgment}는 그 행이 왜 열렸는지의 분포다. 등재 사유 칼럼이 비어 있어
 * (채우는 경로가 없다) 상품의 가장 최근 판정으로 대신한다. 판정을 아직 기다리는 행의 키는
 * {@code NO_VERDICT}다.
 */
public record InspectionBacklogCounts(
        long openCount, LocalDateTime oldestWaitingSince, Map<String, Long> openByLatestJudgment) {

    /** 가장 최근 판정이 없는 열린 행의 키. 셀러가 고친 내용을 AI가 아직 보는 중인 행이다 */
    public static final String NO_VERDICT = "NO_VERDICT";

    public static InspectionBacklogCounts empty() {
        return new InspectionBacklogCounts(0, null, Map.of());
    }
}
