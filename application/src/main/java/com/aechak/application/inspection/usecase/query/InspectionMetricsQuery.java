package com.aechak.application.inspection.usecase.query;

import com.aechak.domain.inspection.enums.InspectionTargetType;
import java.time.LocalDateTime;

/**
 * 지표 조회 조건 — 전부 옵셔널이고 비면 설정 창(기본 24시간)과 전체 축이다.
 *
 * <p>파사드가 기본값을 채우고 폭을 검증한다. 창을 컨트롤러에서 채우면 주기 로그와 화면이 다른
 * 기본값을 쓰게 되고, 그러면 같은 수치를 본다는 전제가 깨진다.
 */
public record InspectionMetricsQuery(
        LocalDateTime from, LocalDateTime to, String policyVersion, InspectionTargetType targetType) {

    /** 설정 창을 그대로 쓰는 조회. 주기 로그가 쓰는 모양이다 */
    public static InspectionMetricsQuery defaults() {
        return new InspectionMetricsQuery(null, null, null, null);
    }

    /**
     * 창도 축도 비운 조회인가.
     *
     * <p>어드민 화면의 기본 조회가 이 모양이고 주기 로그가 쓰는 스냅샷과 같은 한 장이다. 그래서
     * 파사드가 이 조회를 캐시 경로로 보낸다 — 질의 파라미터가 비었다는 것만으로 다른 요청으로
     * 다루면 새로고침 한 번이 전수 집계를 그대로 다시 돌린다.
     */
    public boolean isDefaults() {
        return from == null && to == null && policyVersion == null && targetType == null;
    }
}
