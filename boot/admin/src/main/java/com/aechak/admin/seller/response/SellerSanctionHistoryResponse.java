package com.aechak.admin.seller.response;

import com.aechak.application.seller.usecase.result.SellerSanctionResult;
import com.aechak.domain.seller.sanction.enums.SellerSanctionAction;
import com.aechak.domain.seller.sanction.enums.SellerSanctionReasonCode;
import java.time.LocalDateTime;

/**
 * 판매정지 이력 한 행. action=RELEASE인 행은 reasonCode·effectiveUntil이 비어 있다.
 * memo는 그 조치 시점의 운영자 메모 사본이고 운영자 전용이라 어드민 응답에만 실린다.
 */
public record SellerSanctionHistoryResponse(
        long sanctionId,
        SellerSanctionAction action,
        SellerSanctionReasonCode reasonCode,
        String reasonDetail,
        LocalDateTime effectiveUntil,
        String memo,
        long actedByAdminId,
        LocalDateTime actedAt) {

    public static SellerSanctionHistoryResponse from(SellerSanctionResult result) {
        return new SellerSanctionHistoryResponse(
                result.sanctionId(),
                result.action(),
                result.reasonCode(),
                result.reasonDetail(),
                result.effectiveUntil(),
                result.memo(),
                result.actedByAdminId(),
                result.actedAt());
    }
}
