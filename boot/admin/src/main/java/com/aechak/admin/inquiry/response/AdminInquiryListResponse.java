package com.aechak.admin.inquiry.response;

import com.aechak.application.inquiry.usecase.result.AdminInquirySummaryResult;
import com.aechak.application.support.CursorPageResult;
import java.util.List;

/** totalCount는 첫 페이지에서만 채워진다 — 뒤 페이지에서는 null. */
public record AdminInquiryListResponse(
        List<AdminInquirySummaryResponse> items, Long totalCount, String nextCursor, boolean hasNext) {

    public static AdminInquiryListResponse from(CursorPageResult<AdminInquirySummaryResult> result) {
        return new AdminInquiryListResponse(
                result.getItems().stream()
                        .map(AdminInquirySummaryResponse::from)
                        .toList(),
                result.getTotalCount(),
                result.getNextCursor(),
                result.getHasNext());
    }
}
