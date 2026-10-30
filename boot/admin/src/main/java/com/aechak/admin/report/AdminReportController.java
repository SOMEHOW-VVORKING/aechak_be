package com.aechak.admin.report;

import com.aechak.admin.audit.AdminAudited;
import com.aechak.admin.report.request.DecideReportRequest;
import com.aechak.admin.report.response.AdminReportDetailResponse;
import com.aechak.admin.report.response.AdminReportListResponse;
import com.aechak.application.audit.support.AdminAuditContextHolder;
import com.aechak.application.report.admin.usecase.AdminReportUseCase;
import com.aechak.application.report.admin.usecase.command.DecideReportCommand;
import com.aechak.application.report.admin.usecase.query.ReportSearchQuery;
import com.aechak.application.support.CursorPageSize;
import com.aechak.domain.audit.auditlog.enums.AdminAuditAction;
import com.aechak.domain.audit.auditlog.enums.AdminAuditTargetType;
import com.aechak.domain.report.decision.enums.ReportDecisionReason;
import com.aechak.domain.report.decision.enums.ReportTargetType;
import com.aechak.webcommon.response.ApiResponse;
import com.aechak.websecurity.authentication.AuthPrincipal;
import jakarta.validation.Valid;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 운영자 신고 조회·제재 API — 자격(role=ADMIN)은 모듈 게이트(SecurityConfig)가 보증한다.
 *
 * <p>신고 식별자가 (targetType, reportId) 두 칸인 이유는 상품·리뷰·사용자 신고가 각자 다른 표에
 * 담겨 id가 표마다 1부터 겹치기 때문이다. 한 칸으로 합치려면 세 표를 가로지르는 식별자를
 * 새로 발급해야 하고, 그건 신고 접수 쪽 스키마를 바꾸는 일이다.
 */
@RestController
@RequestMapping("/admin/reports")
@RequiredArgsConstructor
public class AdminReportController {

    private final AdminReportUseCase adminReportUseCase;

    /**
     * 신고 목록 — 접수 역순. targetType·status·size의 형식 오류는 ReportSearchQuery.of가 보정하고,
     * 조건을 바꾼 채 보낸 옛 커서는 400(90002)으로 끊는다.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<AdminReportListResponse>> list(
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate reportedFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate reportedTo,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer size) {
        ReportSearchQuery query = ReportSearchQuery.Companion.of(
                targetType, status, reportedFrom, reportedTo, cursor, size == null ? CursorPageSize.DEFAULT : size);
        return ResponseEntity.ok(
                ApiResponse.Companion.of(AdminReportListResponse.from(adminReportUseCase.list(query))));
    }

    /** 신고 상세 — 없으면 404(140000). 처리가 끝난 신고도 기록과 함께 돌려준다. */
    @GetMapping("/{targetType}/{reportId}")
    public ResponseEntity<ApiResponse<AdminReportDetailResponse>> detail(
            @PathVariable ReportTargetType targetType, @PathVariable long reportId) {
        return ResponseEntity.ok(ApiResponse.Companion.of(
                AdminReportDetailResponse.from(adminReportUseCase.detail(targetType, reportId))));
    }

    /**
     * 처리 결정 — 결정 기록 한 행을 남기고 비노출·제재는 기존 경로에 위임한다.
     * 이미 처리된 신고면 409(140001), 사용자 신고에 비공개 전환을 보내면 400(140002).
     */
    @PostMapping("/{targetType}/{reportId}/decision")
    @AdminAudited(AdminAuditAction.REPORT_DECIDED)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> decide(
            @PathVariable ReportTargetType targetType,
            @PathVariable long reportId,
            @Valid @RequestBody DecideReportRequest request,
            @AuthenticationPrincipal AuthPrincipal principal) {
        // 대상 식별자는 종류와 번호를 합쳐야 가려진다 -- 세 신고 표가 각자 AUTO_INCREMENT 다.
        // 사유는 요청 본문에만 있고, 둘 다 조치보다 먼저 올려야 거부된 시도의 행에도 남는다.
        AdminAuditContextHolder.INSTANCE.target(AdminAuditTargetType.REPORT, targetType.name() + "/" + reportId);
        AdminAuditContextHolder.INSTANCE.reason(auditReason(request.reasonCode(), request.reasonDetail()));
        adminReportUseCase.decide(new DecideReportCommand(
                principal.getUserId(),
                targetType,
                reportId,
                request.decision(),
                request.reasonCode(),
                request.reasonDetail(),
                request.noticeToOwner(),
                request.internalMemo(),
                request.toSanctionDetail()));
        return ResponseEntity.noContent().build();
    }

    /** 감사 행의 사유 칸 -- 고정 어휘를 사람이 읽는 문구로 옮기고 상세가 있으면 이어 붙인다. */
    private static String auditReason(ReportDecisionReason reasonCode, String reasonDetail) {
        String detail = reasonDetail == null || reasonDetail.isBlank() ? null : reasonDetail.strip();
        return detail == null ? reasonCode.getLabel() : reasonCode.getLabel() + " — " + detail;
    }
}
