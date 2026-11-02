package com.aechak.admin.report.request;

import com.aechak.application.report.admin.usecase.command.DecideReportCommand;
import com.aechak.domain.user.sanction.enums.SanctionAction;
import com.aechak.domain.user.sanction.enums.SanctionReasonCode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 계정 제재의 내용 — 회원 제재 API(SanctionMemberRequest)와 같은 어휘를 그대로 받는다.
 *
 * <p>조치별 기간 필수/금지와 종단 상태 판정은 회원 제재 도메인이 한다(30801·30802·30003·30018).
 */
public record ReportSanctionRequest(
        @NotNull SanctionAction action,
        @NotNull SanctionReasonCode reasonCode,
        @Size(max = 500) String reasonDetail,
        @Min(1) @Max(365) Integer durationDays) {

    public DecideReportCommand.SanctionDetail toDetail() {
        return new DecideReportCommand.SanctionDetail(action, reasonCode, reasonDetail, durationDays);
    }
}
