package com.aechak.admin.review.response;

import com.aechak.application.review.admin.usecase.result.AdminReviewSummaryResult;
import com.aechak.application.support.CursorPageResult;
import java.util.List;

/** totalCount는 첫 페이지에만 실린다(커서로 넘어간 뒤에는 null). */
public record AdminReviewListResponse(
        List<AdminReviewSummaryResponse> items, Long totalCount, String nextCursor, boolean hasNext) {

    public static AdminReviewListResponse from(CursorPageResult<AdminReviewSummaryResult> page) {
        return new AdminReviewListResponse(
                page.getItems().stream().map(AdminReviewSummaryResponse::from).toList(),
                page.getTotalCount(),
                page.getNextCursor(),
                page.getHasNext());
    }
}
