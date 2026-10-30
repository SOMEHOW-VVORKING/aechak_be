package com.aechak.admin.inquiry.response;

import com.aechak.application.inquiry.usecase.result.PreviousInquiryResult;
import com.aechak.domain.inquiry.inquiry.enums.InquiryStatus;
import com.aechak.domain.inquiry.inquiry.enums.InquiryType;
import java.time.LocalDateTime;

public record PreviousInquiryResponse(
        long inquiryId, InquiryType inquiryType, InquiryStatus status, LocalDateTime receivedAt) {

    public static PreviousInquiryResponse from(PreviousInquiryResult result) {
        return new PreviousInquiryResponse(
                result.inquiryId(), result.inquiryType(), result.status(), result.receivedAt());
    }
}
