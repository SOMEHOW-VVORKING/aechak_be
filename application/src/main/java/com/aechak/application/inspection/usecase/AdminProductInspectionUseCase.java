package com.aechak.application.inspection.usecase;

import com.aechak.application.inspection.usecase.result.AdminProductInspectionDetailResult;
import com.aechak.application.inspection.usecase.result.AdminProductInspectionSummaryResult;
import com.aechak.application.support.PageQuery;
import com.aechak.application.support.PageResult;
import com.aechak.domain.inspection.enums.ReviewQueueStatus;

/** 어드민 상품 검수 큐. 자격(role=ADMIN)은 어드민 모듈 게이트가 보증함. */
public interface AdminProductInspectionUseCase {

    /**
     * status 미지정(null) 시 전체, 판정 시각 내림차순.
     *
     * appealedOnly는 셀러 이의가 걸린 행만 본다. 등재 사유의 한 축이 아니라 별 축이다 — 대개 이미
     * AI가 가려 열린 행에 이의가 붙으므로, 등재 사유를 덮으면 경로별 유입량을 셀 수 없다.
     */
    PageResult<AdminProductInspectionSummaryResult> list(
            ReviewQueueStatus status, boolean appealedOnly, PageQuery pageQuery);

    /** 상품 본문, 가장 최근 검수, 셀러가 적은 이의 사유, 이전 반려 이력 */
    AdminProductInspectionDetailResult detail(String productPublicId);

    void approve(long adminId, String productPublicId);

    void reject(long adminId, String productPublicId, String reason);
}
