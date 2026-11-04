package com.aechak.admin.product.response;

import com.aechak.application.product.product.admin.usecase.result.AdminProductSummaryResult;
import com.aechak.application.support.PageResult;
import java.util.List;

/** 상품 목록 — totalCount는 필터 기준 전체 행 수다(현재 페이지 크기가 아니다). */
public record AdminProductListResponse(List<AdminProductSummaryResponse> items, long totalCount) {

    public static AdminProductListResponse from(PageResult<AdminProductSummaryResult> result) {
        return new AdminProductListResponse(
                result.getItems().stream()
                        .map(AdminProductSummaryResponse::from)
                        .toList(),
                result.getTotalCount());
    }
}
