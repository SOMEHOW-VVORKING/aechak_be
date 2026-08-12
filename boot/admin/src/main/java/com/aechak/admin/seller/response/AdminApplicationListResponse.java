package com.aechak.admin.seller.response;

import com.aechak.application.seller.usecase.result.AdminApplicationPageResult;
import java.util.List;

public record AdminApplicationListResponse(List<AdminApplicationSummaryResponse> items, long totalCount) {

    public static AdminApplicationListResponse from(AdminApplicationPageResult result) {
        return new AdminApplicationListResponse(
                result.items().stream().map(AdminApplicationSummaryResponse::from).toList(),
                result.totalCount());
    }
}
