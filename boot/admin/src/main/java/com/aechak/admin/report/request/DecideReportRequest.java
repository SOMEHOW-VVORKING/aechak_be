package com.aechak.admin.report.request;

import com.aechak.application.report.admin.usecase.command.DecideReportCommand;
import com.aechak.domain.report.decision.enums.ReportDecisionReason;
import com.aechak.domain.report.decision.enums.ReportDecisionType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 신고 처리 요청.
 *
 * <p>sanction은 decision이 SANCTION_ACCOUNT일 때만 온다 — 필수/금지 판정은 응용 계층이 하고
 * (140003·140004) 여기서는 형식만 막는다.
 */
public record DecideReportRequest(
        @NotNull ReportDecisionType decision,
        @NotNull ReportDecisionReason reasonCode,
        @Size(max = 500) String reasonDetail,
        @Size(max = 300) String noticeToOwner,
        @Size(max = 300) String internalMemo,
        @Valid ReportSanctionRequest sanction) {

    public DecideReportCommand.SanctionDetail toSanctionDetail() {
        return sanction == null ? null : sanction.toDetail();
    }
}
