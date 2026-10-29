package com.aechak.admin.member.response;

import com.aechak.application.support.CursorPageResult;
import com.aechak.application.user.sanction.usecase.result.MemberSummaryResult;
import java.util.List;

public record MemberListResponse(
        List<MemberSummaryResponse> items, Long totalCount, String nextCursor, boolean hasNext) {

    public static MemberListResponse from(CursorPageResult<MemberSummaryResult> result) {
        return new MemberListResponse(
                result.getItems().stream().map(MemberSummaryResponse::from).toList(),
                result.getTotalCount(),
                result.getNextCursor(),
                result.getHasNext());
    }
}
