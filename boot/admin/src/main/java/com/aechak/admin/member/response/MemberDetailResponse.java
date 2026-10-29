package com.aechak.admin.member.response;

import com.aechak.application.user.sanction.usecase.result.MemberDetailResult;
import java.util.List;

public record MemberDetailResponse(MemberSummaryResponse member, List<SanctionHistoryResponse> sanctions) {

    public static MemberDetailResponse from(MemberDetailResult result) {
        return new MemberDetailResponse(
                MemberSummaryResponse.from(result.getMember()),
                result.getSanctions().stream()
                        .map(SanctionHistoryResponse::from)
                        .toList());
    }
}
