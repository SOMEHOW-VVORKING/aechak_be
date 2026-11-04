package com.aechak.admin.seller.response;

import com.aechak.application.seller.usecase.result.AdminSellerDetailResult;
import com.aechak.domain.seller.sanction.enums.SellerSanctionReasonCode;
import java.util.List;

/**
 * 셀러 상세 — 목록 값에 현재 정지의 사유·메모·제재 이력을 더한다.
 *
 * <p>suspensionReasonCode는 화면의 사유 선택을 채우는 값이고, suspensionReason은 사람이 읽는
 * 사유 한 줄(어휘 + 상세)이다. 정지 중이 아니면 둘 다 null이다.
 * sanctions는 최신순이고 최근 50건까지 싣는다.
 */
public record AdminSellerDetailResponse(
        AdminSellerSummaryResponse seller,
        SellerSanctionReasonCode suspensionReasonCode,
        String suspensionReason,
        String internalMemo,
        List<SellerSanctionHistoryResponse> sanctions) {

    public static AdminSellerDetailResponse from(AdminSellerDetailResult result) {
        return new AdminSellerDetailResponse(
                AdminSellerSummaryResponse.from(result.seller()),
                result.suspensionReasonCode(),
                result.suspensionReason(),
                result.internalMemo(),
                result.sanctions().stream()
                        .map(SellerSanctionHistoryResponse::from)
                        .toList());
    }
}
