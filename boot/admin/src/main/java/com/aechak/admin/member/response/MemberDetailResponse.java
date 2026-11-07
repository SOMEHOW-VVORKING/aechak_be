package com.aechak.admin.member.response;

import com.aechak.application.user.sanction.usecase.result.MemberDetailResult;
import java.util.List;

public record MemberDetailResponse(
        MemberSummaryResponse member, String profileImageUrl, String bio, List<SanctionHistoryResponse> sanctions) {

    public static MemberDetailResponse from(MemberDetailResult result) {
        return new MemberDetailResponse(
                MemberSummaryResponse.from(result.getMember()),
                result.getProfileImageUrl(),
                result.getBio(),
                result.getSanctions().stream()
                        .map(SanctionHistoryResponse::from)
                        .toList());
    }
}
