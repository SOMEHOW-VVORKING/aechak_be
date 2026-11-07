package com.aechak.admin.seller.response;

import com.aechak.application.seller.usecase.result.AdminSellerSummaryResult;
import com.aechak.application.support.PageResult;
import java.util.List;

/** 셀러 목록 — totalCount는 필터 기준 전체 행 수다(현재 페이지 크기가 아니다). */
public record AdminSellerListResponse(List<AdminSellerSummaryResponse> items, long totalCount) {

    public static AdminSellerListResponse from(PageResult<AdminSellerSummaryResult> result) {
        return new AdminSellerListResponse(
                result.getItems().stream().map(AdminSellerSummaryResponse::from).toList(), result.getTotalCount());
    }
}
