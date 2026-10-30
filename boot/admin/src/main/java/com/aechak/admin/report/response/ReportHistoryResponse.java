package com.aechak.admin.report.response;

import com.aechak.application.report.admin.usecase.result.ReportHistoryResult;
import com.aechak.domain.report.decision.enums.ReportHandlingStatus;
import java.time.LocalDateTime;

/** 같은 대상에 쌓인 다른 신고 한 건. 지금 보고 있는 건은 빠져 있다. */
public record ReportHistoryResponse(
        long reportId, String reasonCode, String reasonText, LocalDateTime reportedAt, ReportHandlingStatus status) {

    public static ReportHistoryResponse from(ReportHistoryResult result) {
        return new ReportHistoryResponse(
                result.getReportId(),
                result.getReasonCode(),
                result.getReasonText(),
                result.getReportedAt(),
                result.getStatus());
    }
}
