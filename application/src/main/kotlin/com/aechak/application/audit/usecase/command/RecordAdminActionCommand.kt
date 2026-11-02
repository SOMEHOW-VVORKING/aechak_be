package com.aechak.application.audit.usecase.command

import com.aechak.domain.audit.auditlog.enums.AdminAuditAction
import com.aechak.domain.audit.auditlog.enums.AdminAuditResult
import com.aechak.domain.audit.auditlog.enums.AdminAuditTargetType
import java.time.LocalDateTime

/**
 * 운영 조치 한 건을 기록하라는 입력.
 *
 * 대상 종류를 조치에서 끌어오지 않고 따로 받는 이유는, 둘의 짝이 어긋났는지를 도메인이
 * 대조할 재료가 있어야 하기 때문이다 — 호출부가 조치만 주면 어긋남이 생길 자리가 아예 숨는다.
 */
data class RecordAdminActionCommand(
    val actorUserId: Long,
    val action: AdminAuditAction,
    val targetType: AdminAuditTargetType,
    val targetId: String,
    val targetLabel: String?,
    val reason: String?,
    val result: AdminAuditResult,
    val httpStatus: Int,
    val requestId: String?,
    val clientIp: String?,
    val actedAt: LocalDateTime,
)
