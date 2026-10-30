package com.aechak.admin.order.response;

import com.aechak.application.order.admin.usecase.result.AdminOrderSummaryResult;
import com.aechak.application.support.CursorPageResult;
import java.util.List;

/** 커서 페이지. totalCount는 항상 null이다 — 전체 주문 건수를 매 페이지마다 세지 않는다. */
public record AdminOrderListResponse(
        List<AdminOrderSummaryResponse> items, Long totalCount, String nextCursor, boolean hasNext) {

    public static AdminOrderListResponse from(CursorPageResult<AdminOrderSummaryResult> page) {
        return new AdminOrderListResponse(
                page.getItems().stream().map(AdminOrderSummaryResponse::from).toList(),
                page.getTotalCount(),
                page.getNextCursor(),
                page.getHasNext());
    }
}
