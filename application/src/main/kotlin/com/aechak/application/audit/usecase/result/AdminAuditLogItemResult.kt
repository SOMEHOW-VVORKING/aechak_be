package com.aechak.application.audit.usecase.result

import com.aechak.application.audit.port.view.AdminAuditLogRowView
import com.aechak.domain.audit.auditlog.enums.AdminAuditAction
import com.aechak.domain.audit.auditlog.enums.AdminAuditResult
import com.aechak.domain.audit.auditlog.enums.AdminAuditTargetType
import com.aechak.domain.user.user.enums.UserRole
import java.time.LocalDateTime

/** 감사 로그 한 행. 조치·대상 종류·결과는 열거 그대로 싣고 사람이 읽는 이름을 함께 준다 */
data class AdminAuditLogItemResult(
    val id: String,
    val actedAt: LocalDateTime,
    val actorPublicId: String?,
    val actorNickname: String?,
    val actorRole: UserRole?,
    val action: AdminAuditAction,
    val actionLabel: String,
    val targetType: AdminAuditTargetType,
    val targetTypeLabel: String,
    val targetId: String,
    val targetLabel: String?,
    val reason: String?,
    val result: AdminAuditResult,
    val httpStatus: Int,
    val requestId: String?,
    val clientIp: String?,
) {
    companion object {
        fun from(view: AdminAuditLogRowView): AdminAuditLogItemResult =
            AdminAuditLogItemResult(
                id = view.publicId,
                actedAt = view.actedAt,
                actorPublicId = view.actorPublicId,
                actorNickname = view.actorNickname,
                actorRole = view.actorRole,
                action = view.action,
                actionLabel = view.action.label,
                targetType = view.targetType,
                targetTypeLabel = view.targetType.label,
                targetId = view.targetId,
                targetLabel = view.targetLabel,
                reason = view.reason,
                result = view.result,
                httpStatus = view.httpStatus,
                requestId = view.requestId,
                clientIp = view.clientIp,
            )
    }
}
