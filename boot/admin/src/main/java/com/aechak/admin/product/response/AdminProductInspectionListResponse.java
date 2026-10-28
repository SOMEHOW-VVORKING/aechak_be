package com.aechak.admin.product.response;

import com.aechak.application.inspection.usecase.result.AdminProductInspectionSummaryResult;
import com.aechak.application.support.PageResult;
import java.util.List;

public record AdminProductInspectionListResponse(List<AdminProductInspectionSummaryResponse> items, long totalCount) {

    public static AdminProductInspectionListResponse from(PageResult<AdminProductInspectionSummaryResult> result) {
        return new AdminProductInspectionListResponse(
                result.getItems().stream()
                        .map(AdminProductInspectionSummaryResponse::from)
                        .toList(),
                result.getTotalCount());
    }
}
