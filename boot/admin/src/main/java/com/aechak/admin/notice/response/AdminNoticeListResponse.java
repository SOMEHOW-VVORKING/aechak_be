package com.aechak.admin.notice.response;

import com.aechak.application.notice.admin.usecase.result.AdminNoticeSummaryResult;
import com.aechak.application.support.CursorPageResult;
import java.util.List;

/** totalCount는 첫 페이지에서만 채워진다 — 뒤 페이지에서는 null. */
public record AdminNoticeListResponse(
        List<AdminNoticeSummaryResponse> items, Long totalCount, String nextCursor, boolean hasNext) {

    public static AdminNoticeListResponse from(CursorPageResult<AdminNoticeSummaryResult> result) {
        return new AdminNoticeListResponse(
                result.getItems().stream().map(AdminNoticeSummaryResponse::from).toList(),
                result.getTotalCount(),
                result.getNextCursor(),
                result.getHasNext());
    }
}
