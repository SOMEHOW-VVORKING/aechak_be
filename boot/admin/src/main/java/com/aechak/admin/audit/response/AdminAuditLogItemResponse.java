package com.aechak.admin.audit.response;

import com.aechak.application.audit.usecase.result.AdminAuditLogItemResult;
import com.aechak.domain.audit.auditlog.enums.AdminAuditAction;
import com.aechak.domain.audit.auditlog.enums.AdminAuditResult;
import com.aechak.domain.audit.auditlog.enums.AdminAuditTargetType;
import com.aechak.domain.user.user.enums.UserRole;
import java.time.LocalDateTime;

/**
 * 감사 로그 한 행.
 *
 * <p>열거(action·targetType·result·actorRole)는 열거 타입 그대로 싣는다 — {@code .name()}으로
 * 문자열화하면 생성 문서에 허용값이 실리지 않는다.
 *
 * <p>{@code actorNickname}·{@code actorRole}은 조치한 계정의 <b>현재</b> 값이고, 그 계정이
 * 지워졌으면 둘 다 null이다(행은 남는다). {@code actorPublicId}도 그때 null이다.
 * {@code targetLabel}은 기록 시점의 대상 이름이라 대상이 개명·삭제돼도 그때의 이름이 남는다.
 */
public record AdminAuditLogItemResponse(
        String id,
        LocalDateTime actedAt,
        String actorPublicId,
        String actorNickname,
        UserRole actorRole,
        AdminAuditAction action,
        String actionLabel,
        AdminAuditTargetType targetType,
        String targetTypeLabel,
        String targetId,
        String targetLabel,
        String reason,
        AdminAuditResult result,
        int httpStatus,
        String requestId,
        String clientIp) {

    public static AdminAuditLogItemResponse from(AdminAuditLogItemResult result) {
        return new AdminAuditLogItemResponse(
                result.getId(),
                result.getActedAt(),
                result.getActorPublicId(),
                result.getActorNickname(),
                result.getActorRole(),
                result.getAction(),
                result.getActionLabel(),
                result.getTargetType(),
                result.getTargetTypeLabel(),
                result.getTargetId(),
                result.getTargetLabel(),
                result.getReason(),
                result.getResult(),
                result.getHttpStatus(),
                result.getRequestId(),
                result.getClientIp());
    }
}
