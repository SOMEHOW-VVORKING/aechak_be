package com.aechak.admin.review.response;

import com.aechak.application.review.admin.usecase.result.AdminReviewReportResult;
import com.aechak.domain.review.report.enums.ReviewReportReason;
import com.aechak.domain.review.report.enums.ReviewReportStatus;
import java.time.LocalDateTime;

public record AdminReviewReportResponse(
        long reportId,
        long reporterId,
        ReviewReportReason reasonCode,
        String reasonText,
        ReviewReportStatus status,
        LocalDateTime reportedAt) {

    public static AdminReviewReportResponse from(AdminReviewReportResult result) {
        return new AdminReviewReportResponse(
                result.reportId(),
                result.reporterId(),
                result.reasonCode(),
                result.reasonText(),
                result.status(),
                result.reportedAt());
    }
}
