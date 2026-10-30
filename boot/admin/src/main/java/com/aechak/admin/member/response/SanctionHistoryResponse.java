package com.aechak.admin.member.response;

import com.aechak.application.user.sanction.usecase.result.SanctionHistoryResult;
import com.aechak.domain.user.sanction.enums.SanctionAction;
import com.aechak.domain.user.sanction.enums.SanctionReasonCode;
import java.time.LocalDateTime;

/** 제재 이력 한 행. reasonCode가 null이면 해제 행이다. memo는 운영자 전용이라 어드민 응답에만 실린다. */
public record SanctionHistoryResponse(
        long sanctionId,
        SanctionAction action,
        SanctionReasonCode reasonCode,
        String reasonDetail,
        LocalDateTime effectiveUntil,
        String memo,
        long actedByAdminId,
        LocalDateTime actedAt) {

    public static SanctionHistoryResponse from(SanctionHistoryResult result) {
        return new SanctionHistoryResponse(
                result.getSanctionId(),
                result.getAction(),
                result.getReasonCode(),
                result.getReasonDetail(),
                result.getEffectiveUntil(),
                result.getMemo(),
                result.getActedByAdminId(),
                result.getActedAt());
    }
}
