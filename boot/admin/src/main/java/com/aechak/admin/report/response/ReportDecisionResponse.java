package com.aechak.admin.report.response;

import com.aechak.application.report.admin.usecase.result.DecisionRecordResult;
import com.aechak.domain.report.decision.enums.ReportDecisionReason;
import com.aechak.domain.report.decision.enums.ReportDecisionType;
import java.time.LocalDateTime;

/** 이미 내려진 처리 결정. 어드민 전용 경로라 내부 메모까지 내려준다. */
public record ReportDecisionResponse(
        ReportDecisionType decision,
        ReportDecisionReason reasonCode,
        String reasonDetail,
        String noticeToOwner,
        String internalMemo,
        long targetOwnerUserId,
        long actedByAdminId,
        LocalDateTime decidedAt) {

    public static ReportDecisionResponse from(DecisionRecordResult result) {
        return new ReportDecisionResponse(
                result.getDecision(),
                result.getReasonCode(),
                result.getReasonDetail(),
                result.getNoticeToOwner(),
                result.getInternalMemo(),
                result.getTargetOwnerUserId(),
                result.getActedByAdminId(),
                result.getDecidedAt());
    }
}
