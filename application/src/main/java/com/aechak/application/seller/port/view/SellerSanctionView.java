package com.aechak.application.seller.port.view;

import com.aechak.domain.seller.sanction.enums.SellerSanctionAction;
import com.aechak.domain.seller.sanction.enums.SellerSanctionReasonCode;
import java.time.LocalDateTime;

/** 판매정지 이력 한 행. reasonCode가 비어 있으면 해제 행이다. */
public record SellerSanctionView(
        long sanctionId,
        SellerSanctionAction action,
        SellerSanctionReasonCode reasonCode,
        String reasonDetail,
        LocalDateTime effectiveUntil,
        String memo,
        long actedByAdminId,
        LocalDateTime actedAt) {}
