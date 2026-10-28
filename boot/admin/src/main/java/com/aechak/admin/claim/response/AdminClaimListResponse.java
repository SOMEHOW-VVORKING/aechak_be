package com.aechak.admin.claim.response;

import com.aechak.application.order.claim.admin.usecase.result.AdminClaimPageResult;
import java.util.List;

/** 커서 페이징 목록. nextCursor는 hasNext가 true일 때만 채워진다. */
public record AdminClaimListResponse(
        List<AdminClaimSummaryResponse> items, Long totalCount, String nextCursor, boolean hasNext) {

    public static AdminClaimListResponse from(AdminClaimPageResult result) {
        var page = result.getPage();
        return new AdminClaimListResponse(
                page.getItems().stream().map(AdminClaimSummaryResponse::from).toList(),
                page.getTotalCount(),
                page.getNextCursor(),
                page.getHasNext());
    }
}
