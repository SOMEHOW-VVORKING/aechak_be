package com.aechak.admin.audit;

import com.aechak.admin.audit.response.AdminAuditLogListResponse;
import com.aechak.application.audit.usecase.AdminAuditLogUseCase;
import com.aechak.application.audit.usecase.query.AdminAuditLogListQuery;
import com.aechak.application.support.CursorPageSize;
import com.aechak.domain.audit.auditlog.enums.AdminAuditAction;
import com.aechak.domain.audit.auditlog.enums.AdminAuditResult;
import com.aechak.domain.audit.auditlog.enums.AdminAuditTargetType;
import com.aechak.webcommon.response.ApiResponse;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 감사 로그 조회 API — <b>읽기만 있다.</b>
 *
 * <p>쓰기 핸들러를 두지 않는 것이 의도다. 사후에 고칠 수 있는 기록은 분쟁에서 증거가 되지 못한다.
 * 행을 더하는 경로는 운영 조치 자체뿐이고({@link AdminAuditInterceptor}), 고치거나 지우는 경로는
 * 애플리케이션·도메인 포트에도 없다.
 *
 * <p>자격(role=ADMIN)은 어드민 모듈 SecurityConfig가 전역 보증한다.
 */
@RestController
@RequestMapping("/admin/audit-logs")
@RequiredArgsConstructor
public class AdminAuditLogController {

    private final AdminAuditLogUseCase adminAuditLogUseCase;

    /**
     * 기록 순서 내림차순(최근 기록이 먼저) 커서 페이징. 동시 조치에서는 이 순서와 {@code actedAt}
     * 순서가 밀리초 단위로 어긋날 수 있다 — 기록 순서로 읽으면 늦게 들어온 행을 건너뛰지 않는다.
     *
     * <p>필터는 모두 AND다. {@code action}과 {@code targetType}을 함께 주면 짝이 맞아야 한다 —
     * 어긋난 조합은 언제나 0건이므로 조용히 빈 목록을 주는 대신 400으로 끊는다.
     * {@code from}·{@code to}는 조치 시각 기준 닫힌 구간이고 뒤집힌 구간도 400이다.
     * {@code keyword}는 대상 이름·대상 식별자·사유를 글자 그대로 부분 일치한다.
     *
     * <p>필터를 바꾼 채 이전 {@code cursor}를 보내면 90002로 끊는다 — 다른 조건의 행 id로 이어
     * 읽으면 운영자가 빠진 구간을 알 수 없다.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<AdminAuditLogListResponse>> list(
            @RequestParam(required = false) String actorPublicId,
            @RequestParam(required = false) AdminAuditTargetType targetType,
            @RequestParam(required = false) AdminAuditAction action,
            @RequestParam(required = false) AdminAuditResult result,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "" + CursorPageSize.DEFAULT) int size) {
        AdminAuditLogListQuery query =
                new AdminAuditLogListQuery(actorPublicId, targetType, action, result, from, to, keyword, cursor, size);
        return ResponseEntity.ok(
                ApiResponse.Companion.of(AdminAuditLogListResponse.from(adminAuditLogUseCase.list(query))));
    }
}
