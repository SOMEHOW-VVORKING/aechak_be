package com.aechak.admin.report.response;

import com.aechak.application.report.admin.usecase.result.ReportSummaryResult;
import com.aechak.application.support.CursorPageResult;
import java.util.List;

public record AdminReportListResponse(
        List<AdminReportSummaryResponse> items, Long totalCount, String nextCursor, boolean hasNext) {

    public static AdminReportListResponse from(CursorPageResult<ReportSummaryResult> result) {
        return new AdminReportListResponse(
                result.getItems().stream().map(AdminReportSummaryResponse::from).toList(),
                result.getTotalCount(),
                result.getNextCursor(),
                result.getHasNext());
    }
}
