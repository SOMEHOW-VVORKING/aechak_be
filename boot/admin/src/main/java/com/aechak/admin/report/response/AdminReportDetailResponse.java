package com.aechak.admin.report.response;

import com.aechak.application.report.admin.usecase.result.ReportDetailResult;
import java.util.List;

/** 신고 상세 — 신고 내용과 대상, 같은 대상의 누적 신고 이력, 이미 내려진 결정. */
public record AdminReportDetailResponse(
        AdminReportSummaryResponse report,
        long reporterId,
        String reasonText,
        List<ReportHistoryResponse> history,
        ReportDecisionResponse decision) {

    public static AdminReportDetailResponse from(ReportDetailResult result) {
        return new AdminReportDetailResponse(
                AdminReportSummaryResponse.from(result.getReport()),
                result.getReporterId(),
                result.getReasonText(),
                result.getHistory().stream().map(ReportHistoryResponse::from).toList(),
                result.getDecision() == null ? null : ReportDecisionResponse.from(result.getDecision()));
    }
}
