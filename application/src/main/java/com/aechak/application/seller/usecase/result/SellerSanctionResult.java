package com.aechak.application.seller.usecase.result;

import com.aechak.application.seller.port.view.SellerSanctionView;
import com.aechak.domain.seller.sanction.enums.SellerSanctionAction;
import com.aechak.domain.seller.sanction.enums.SellerSanctionReasonCode;
import java.time.LocalDateTime;

/** 판매정지 이력 한 행. reasonCode가 비어 있으면 해제 행이다. memo는 운영자 전용이라 어드민 응답에만 실린다. */
public record SellerSanctionResult(
        long sanctionId,
        SellerSanctionAction action,
        SellerSanctionReasonCode reasonCode,
        String reasonDetail,
        LocalDateTime effectiveUntil,
        String memo,
        long actedByAdminId,
        LocalDateTime actedAt) {

    public static SellerSanctionResult from(SellerSanctionView view) {
        return new SellerSanctionResult(
                view.sanctionId(),
                view.action(),
                view.reasonCode(),
                view.reasonDetail(),
                view.effectiveUntil(),
                view.memo(),
                view.actedByAdminId(),
                view.actedAt());
    }
}
