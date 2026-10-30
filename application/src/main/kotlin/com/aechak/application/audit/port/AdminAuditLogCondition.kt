package com.aechak.application.audit.port

import com.aechak.domain.audit.auditlog.enums.AdminAuditAction
import com.aechak.domain.audit.auditlog.enums.AdminAuditResult
import com.aechak.domain.audit.auditlog.enums.AdminAuditTargetType
import java.time.LocalDateTime

/** 감사 로그 조회 조건. null 필드는 거르지 않는다 */
data class AdminAuditLogCondition(
    val actorPublicId: String? = null,
    val targetType: AdminAuditTargetType? = null,
    val action: AdminAuditAction? = null,
    val result: AdminAuditResult? = null,
    val from: LocalDateTime? = null,
    val to: LocalDateTime? = null,
    /** 대상 이름·대상 식별자·사유를 글자 그대로 부분 일치 */
    val keyword: String? = null,
)
