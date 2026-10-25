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
}
