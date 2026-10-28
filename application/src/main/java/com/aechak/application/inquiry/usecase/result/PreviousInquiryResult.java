package com.aechak.application.inquiry.usecase.result;

import com.aechak.application.inquiry.port.view.PreviousInquiryView;
import com.aechak.domain.inquiry.inquiry.enums.InquiryStatus;
import com.aechak.domain.inquiry.inquiry.enums.InquiryType;
import java.time.LocalDateTime;

/** 같은 작성자의 과거 문의 한 줄 — 반복 문의 판단 보조(차단 아님). */
public record PreviousInquiryResult(
        long inquiryId, InquiryType inquiryType, InquiryStatus status, LocalDateTime receivedAt) {

    public static PreviousInquiryResult from(PreviousInquiryView view) {
        return new PreviousInquiryResult(
                view.getInquiryId(), view.getInquiryType(), view.getStatus(), view.getCreatedAt());
    }
}
