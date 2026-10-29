package com.aechak.application.inquiry.usecase.result;

import com.aechak.domain.inquiry.inquiry.Inquiry;
import com.aechak.domain.inquiry.inquiry.enums.InquiryStatus;
import com.aechak.domain.inquiry.inquiry.enums.InquiryType;
import java.time.LocalDateTime;
import java.util.List;

/** 문의 상세 — 전문·작성자·답변 기록·같은 작성자의 과거 문의. */
public record AdminInquiryDetailResult(
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
        AdminInquiryAuthorResult author,
        List<PreviousInquiryResult> previousInquiries) {

    public static AdminInquiryDetailResult from(
            Inquiry inquiry, AdminInquiryAuthorResult author, List<PreviousInquiryResult> previousInquiries) {
        return new AdminInquiryDetailResult(
                inquiry.getId(),
                inquiry.getInquiryType(),
                inquiry.getStatus(),
                inquiry.getContent(),
                inquiry.getReplyEmail(),
                inquiry.getCreatedAt(),
                inquiry.getAnswer(),
                inquiry.getAnsweredByAdminId(),
                inquiry.getAssignedAdminId(),
                inquiry.getResolvedAt(),
                author,
                previousInquiries);
    }
}
