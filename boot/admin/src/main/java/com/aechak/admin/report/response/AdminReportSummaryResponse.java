package com.aechak.admin.report.response;

import com.aechak.application.report.admin.usecase.result.ReportSummaryResult;
import com.aechak.domain.report.decision.enums.ReportDecisionReason;
import com.aechak.domain.report.decision.enums.ReportDecisionType;
import com.aechak.domain.report.decision.enums.ReportHandlingStatus;
import com.aechak.domain.report.decision.enums.ReportTargetType;
import java.time.LocalDateTime;

/**
 * 목록·상세의 신고 한 건.
 *
 * <p>targetKey는 화면이 대상을 다시 열 때 쓰는 식별자다 — 상품은 공개 식별자(ULID), 리뷰와 사용자는
 * 내부 id다. targetName은 상품명·리뷰 본문이고 사용자 신고에는 없다(닉네임이 그 자리를 대신한다).
 * reasonCode는 대상 종류별 신고 사유 어휘라 종류에 따라 허용값이 다르다.
 */
public record AdminReportSummaryResponse(
        ReportTargetType targetType,
        long reportId,
        String targetKey,
        String targetName,
        long targetOwnerUserId,
        String targetOwnerNickname,
        String reasonCode,
        long reportCount,
        LocalDateTime reportedAt,
        ReportHandlingStatus status,
        ReportDecisionType decision,
        ReportDecisionReason decisionReasonCode,
        LocalDateTime decidedAt) {

    public static AdminReportSummaryResponse from(ReportSummaryResult result) {
        return new AdminReportSummaryResponse(
                result.getTargetType(),
                result.getReportId(),
                result.getTargetKey(),
                result.getTargetName(),
                result.getTargetOwnerUserId(),
                result.getTargetOwnerNickname(),
                result.getReasonCode(),
                result.getReportCount(),
                result.getReportedAt(),
                result.getStatus(),
                result.getDecision(),
                result.getDecisionReasonCode(),
                result.getDecidedAt());
    }
}
