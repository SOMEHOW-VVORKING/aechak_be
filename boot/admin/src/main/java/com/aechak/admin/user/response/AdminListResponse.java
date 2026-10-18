package com.aechak.admin.user.response;

import com.aechak.application.support.PageResult;
import com.aechak.application.user.user.usecase.result.AdminUserRoleResult;
import java.util.List;

public record AdminListResponse(List<AdminSummaryResponse> items, long totalCount) {

    public static AdminListResponse from(PageResult<AdminUserRoleResult> result, long currentAdminId) {
        return new AdminListResponse(
                result.getItems().stream()
                        .map(item -> AdminSummaryResponse.from(item, currentAdminId))
                        .toList(),
                result.getTotalCount());
    }
}
