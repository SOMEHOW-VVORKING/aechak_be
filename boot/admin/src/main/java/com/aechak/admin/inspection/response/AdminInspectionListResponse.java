package com.aechak.admin.inspection.response;

import com.aechak.application.inspection.usecase.result.AdminInspectionSummaryResult;
import com.aechak.application.support.PageResult;
import java.util.List;

public record AdminInspectionListResponse(List<AdminInspectionSummaryResponse> items, long totalCount) {

    public static AdminInspectionListResponse from(PageResult<AdminInspectionSummaryResult> result) {
        return new AdminInspectionListResponse(
                result.getItems().stream()
                        .map(AdminInspectionSummaryResponse::from)
                        .toList(),
                result.getTotalCount());
    }
}
