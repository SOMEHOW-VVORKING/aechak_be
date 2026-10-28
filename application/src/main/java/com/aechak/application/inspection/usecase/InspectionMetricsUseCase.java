package com.aechak.application.inspection.usecase;

import com.aechak.application.inspection.usecase.query.InspectionMetricsQuery;
import com.aechak.application.inspection.usecase.result.InspectionMetricsResult;

/**
 * 검수 운영 지표 조회 — 단계별 통과율·승급률·백로그 적체·비용.
 *
 * <p>읽는 길이 둘이다. 관리자가 화면에서 한 번 보는 길과, 실행 모듈이 주기적으로 로그에 찍는
 * 길이다. 둘이 같은 유스케이스를 쓴다 — 갈라 두면 화면과 로그의 수치가 어긋난다. 조회만 두면
 * 아무도 열어 보지 않을 때 쌓인 것이 드러나지 않고, 그러면 "사람 검수 단계가 있다"는 말이
 * 사실이 아니게 된다.
 */
public interface InspectionMetricsUseCase {

    /** 설정된 집계 창 기준 지표 한 장. 주기 로그가 쓰는 길이다 */
    InspectionMetricsResult snapshot();

    /** 창과 축을 지정한 지표 한 장. 비운 항목은 설정 창과 전체 축으로 채운다 */
    InspectionMetricsResult snapshot(InspectionMetricsQuery query);
}
