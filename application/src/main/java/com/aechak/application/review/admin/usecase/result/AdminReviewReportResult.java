package com.aechak.application.review.admin.usecase.result;

import com.aechak.application.review.admin.port.view.AdminReviewReportView;
import com.aechak.domain.review.report.enums.ReviewReportReason;
import com.aechak.domain.review.report.enums.ReviewReportStatus;
import java.time.LocalDateTime;

public record AdminReviewReportResult(
        long reportId,
        long reporterId,
        ReviewReportReason reasonCode,
        String reasonText,
        ReviewReportStatus status,
        LocalDateTime reportedAt) {

    public static AdminReviewReportResult from(AdminReviewReportView view) {
        return new AdminReviewReportResult(
                view.reportId(),
                view.reporterId(),
                view.reasonCode(),
                view.reasonText(),
                view.status(),
                view.createdAt());
    }
}
