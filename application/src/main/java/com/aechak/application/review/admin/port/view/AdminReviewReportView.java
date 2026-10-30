package com.aechak.application.review.admin.port.view;

import com.aechak.domain.review.report.enums.ReviewReportReason;
import com.aechak.domain.review.report.enums.ReviewReportStatus;
import java.time.LocalDateTime;

public record AdminReviewReportView(
        long reportId,
        long reporterId,
        ReviewReportReason reasonCode,
        String reasonText,
        ReviewReportStatus status,
        LocalDateTime createdAt) {}
