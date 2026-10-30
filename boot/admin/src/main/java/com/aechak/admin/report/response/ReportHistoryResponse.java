package com.aechak.admin.report.response;

import com.aechak.application.report.admin.usecase.result.ReportHistoryResult;
import com.aechak.domain.report.decision.enums.ReportHandlingStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/** 같은 대상에 쌓인 다른 신고 한 건. 지금 보고 있는 건은 빠져 있다. */
public record ReportHistoryResponse(
        long reportId,
        @Schema(
                        description = "접수된 신고의 사유. 대상 종류별 어휘라 종류에 따라 허용값이 갈린다 — "
                                + "PRODUCT=FALSE_AD·INFO_ERROR·ILLEGAL·INAPPROPRIATE·OTHER, "
                                + "REVIEW·USER=FRAUD·ABUSE·SPAM·INAPPROPRIATE·OTHER. "
                                + "허용값은 세 어휘의 합집합이다.",
                        allowableValues = {
                            "ABUSE",
                            "FALSE_AD",
                            "FRAUD",
                            "ILLEGAL",
                            "INAPPROPRIATE",
                            "INFO_ERROR",
                            "OTHER",
                            "SPAM"
                        })
                String reasonCode,
        String reasonText,
        LocalDateTime reportedAt,
        ReportHandlingStatus status) {

    public static ReportHistoryResponse from(ReportHistoryResult result) {
        return new ReportHistoryResponse(
                result.getReportId(),
                result.getReasonCode(),
                result.getReasonText(),
                result.getReportedAt(),
                result.getStatus());
    }
}
