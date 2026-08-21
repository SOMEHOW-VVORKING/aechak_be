package com.aechak.admin.seller.response;

import com.aechak.application.seller.usecase.result.AdminApplicationSummaryResult;
import com.aechak.application.support.PageResult;
import java.util.List;

public record AdminApplicationListResponse(List<AdminApplicationSummaryResponse> items, long totalCount) {

    public static AdminApplicationListResponse from(PageResult<AdminApplicationSummaryResult> result) {
        return new AdminApplicationListResponse(
                result.getItems().stream().map(AdminApplicationSummaryResponse::from).toList(),
                result.getTotalCount());
    }
}
