package com.aechak.admin.inquiry.response;

import com.aechak.application.inquiry.usecase.result.AdminInquirySummaryResult;
import com.aechak.domain.inquiry.inquiry.enums.InquiryStatus;
import com.aechak.domain.inquiry.inquiry.enums.InquiryType;
import java.time.LocalDateTime;

public record AdminInquirySummaryResponse(
        long inquiryId,
        InquiryType inquiryType,
        InquiryStatus status,
        AdminInquiryAuthorResponse author,
        String contentPreview,
        Long assignedAdminId,
        Long answeredByAdminId,
        LocalDateTime receivedAt,
        LocalDateTime resolvedAt) {

    public static AdminInquirySummaryResponse from(AdminInquirySummaryResult result) {
        return new AdminInquirySummaryResponse(
                result.inquiryId(),
                result.inquiryType(),
                result.status(),
                AdminInquiryAuthorResponse.from(result.author()),
                result.contentPreview(),
                result.assignedAdminId(),
                result.answeredByAdminId(),
                result.receivedAt(),
                result.resolvedAt());
    }
}
