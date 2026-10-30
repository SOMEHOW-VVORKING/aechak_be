package com.aechak.admin.inquiry.response;

import com.aechak.application.inquiry.usecase.result.AdminInquiryDetailResult;
import com.aechak.domain.inquiry.inquiry.enums.InquiryStatus;
import com.aechak.domain.inquiry.inquiry.enums.InquiryType;
import java.time.LocalDateTime;
import java.util.List;

/** answer·answeredByAdminId·resolvedAt은 답변 등록 전까지 null이다. */
public record AdminInquiryDetailResponse(
        long inquiryId,
        InquiryType inquiryType,
        InquiryStatus status,
        String content,
        String replyEmail,
        LocalDateTime receivedAt,
        String answer,
        Long answeredByAdminId,
        Long assignedAdminId,
        LocalDateTime resolvedAt,
        AdminInquiryAuthorResponse author,
        List<PreviousInquiryResponse> previousInquiries) {

    public static AdminInquiryDetailResponse from(AdminInquiryDetailResult result) {
        return new AdminInquiryDetailResponse(
                result.inquiryId(),
                result.inquiryType(),
                result.status(),
                result.content(),
                result.replyEmail(),
                result.receivedAt(),
                result.answer(),
                result.answeredByAdminId(),
                result.assignedAdminId(),
                result.resolvedAt(),
                AdminInquiryAuthorResponse.from(result.author()),
                result.previousInquiries().stream()
                        .map(PreviousInquiryResponse::from)
                        .toList());
    }
}
