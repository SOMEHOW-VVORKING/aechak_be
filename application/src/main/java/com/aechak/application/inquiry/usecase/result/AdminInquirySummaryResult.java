package com.aechak.application.inquiry.usecase.result;

import com.aechak.application.inquiry.port.view.AdminInquiryView;
import com.aechak.domain.inquiry.inquiry.enums.InquiryStatus;
import com.aechak.domain.inquiry.inquiry.enums.InquiryType;
import java.time.LocalDateTime;

/**
 * 목록 한 줄. 문의에 제목 필드가 없어 내용 앞머리를 제목 자리에 쓴다 — 전문은 상세에서 읽는다.
 *
 * @param contentPreview 내용 앞 {@value #PREVIEW_LENGTH}자. 잘렸으면 끝에 줄임표가 붙는다.
 */
public record AdminInquirySummaryResult(
        long inquiryId,
        InquiryType inquiryType,
        InquiryStatus status,
        AdminInquiryAuthorResult author,
        String contentPreview,
        Long assignedAdminId,
        Long answeredByAdminId,
        LocalDateTime receivedAt,
        LocalDateTime resolvedAt) {

    public static final int PREVIEW_LENGTH = 100;

    private static final String ELLIPSIS = "…";

    public static AdminInquirySummaryResult from(AdminInquiryView view, AdminInquiryAuthorResult author) {
        return new AdminInquirySummaryResult(
                view.getInquiryId(),
                view.getInquiryType(),
                view.getStatus(),
                author,
                preview(view.getContent()),
                view.getAssignedAdminId(),
                view.getAnsweredByAdminId(),
                view.getCreatedAt(),
                view.getResolvedAt());
    }

    /** 줄바꿈이 섞인 본문이 목록에서 한 줄을 넘지 않게 공백으로 평탄화한 뒤 자른다. */
    private static String preview(String content) {
        String flattened = content.replaceAll("\\s+", " ").trim();
        return flattened.length() <= PREVIEW_LENGTH ? flattened : flattened.substring(0, PREVIEW_LENGTH) + ELLIPSIS;
    }
}
