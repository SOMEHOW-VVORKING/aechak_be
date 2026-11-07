package com.aechak.application.inspection.usecase;

import com.aechak.application.inspection.usecase.query.AdminInspectionSearchQuery;
import com.aechak.application.inspection.usecase.result.AdminInspectionDetailResult;
import com.aechak.application.inspection.usecase.result.AdminInspectionSummaryResult;
import com.aechak.application.support.PageResult;

/**
 * 검수 진행 현황 조회. 자격(role=ADMIN)은 어드민 모듈 게이트가 보증한다.
 *
 * <p>같은 BC의 다른 두 조회와 묻는 것이 다르다. 운영 지표({@code InspectionMetricsUseCase})는
 * 닫힌 창의 집계라 끝난 건만 세고, 검토 큐({@code AdminProductInspectionUseCase})는 사람 확인이
 * 남은 행만 든다. 여기는 검수 건 하나하나의 진행 상태라 아직 돌고 있는 건이 주인공이다.
 */
public interface AdminInspectionQueryUseCase {

    /** 요청 시각 내림차순 고정. 방금 등록한 건이 맨 위에 와야 하는 화면이다 */
    PageResult<AdminInspectionSummaryResult> list(AdminInspectionSearchQuery query);

    /** 없으면 40508 */
    AdminInspectionDetailResult detail(long inspectionId);
}
