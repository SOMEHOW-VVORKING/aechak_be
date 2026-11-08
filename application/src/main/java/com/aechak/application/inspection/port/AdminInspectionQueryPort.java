package com.aechak.application.inspection.port;

import com.aechak.application.inspection.port.view.AdminInspectionDetailView;
import com.aechak.application.inspection.port.view.AdminInspectionRowView;
import java.util.List;

/**
 * 검수 진행 현황 조회. 쓰기는 없다.
 *
 * <p>대상 이름은 질의가 조인으로 함께 가져온다. 행마다 상품을 따로 물으면 한 페이지에 조회가 행
 * 수만큼 늘고, 그 비용이 목록 폭을 늘릴 때마다 커진다.
 */
public interface AdminInspectionQueryPort {

    /** 요청 시각 내림차순. 같은 시각이면 검수 식별자 내림차순이다 */
    List<AdminInspectionRowView> findPage(AdminInspectionPageCondition condition);

    long count(AdminInspectionPageCondition condition);

    /** 없으면 null */
    AdminInspectionDetailView findDetail(long inspectionId);
}
