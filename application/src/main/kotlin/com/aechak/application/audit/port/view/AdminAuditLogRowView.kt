package com.aechak.application.audit.port.view

import com.aechak.domain.audit.auditlog.enums.AdminAuditAction
import com.aechak.domain.audit.auditlog.enums.AdminAuditResult
import com.aechak.domain.audit.auditlog.enums.AdminAuditTargetType
import com.aechak.domain.user.user.enums.UserRole
import java.time.LocalDateTime

/**
 * 감사 로그 한 행 + 조치한 운영자의 신원.
 *
 * [actorNickname]·[actorRole]은 users·user_profiles를 LEFT JOIN해 **지금** 읽은 값이다.
 * 조치 시점의 값이 아니다 — 이 레포의 역할은 GENERAL·ADMIN 둘뿐이고 조치는 어드민 게이트
 * 뒤에서만 일어나므로 조치 시점에 얼릴 만한 구분이 아직 없다. 역할 체계가 세분화되면
 * 그때 조치 시점의 역할을 행에 적어야 한다(현재 값으로는 "그때 그 권한이 있었나"를 못 답한다).
 * 조치한 계정이 지워지면 둘 다 null이고 [actorUserId]만 남는다.
 */
data class AdminAuditLogRowView(
    val id: Long,
    val publicId: String,
    val actedAt: LocalDateTime,
    val actorUserId: Long,
    val actorPublicId: String?,
    val actorNickname: String?,
    val actorRole: UserRole?,
    val action: AdminAuditAction,
    val targetType: AdminAuditTargetType,
    val targetId: String,
    val targetLabel: String?,
    val reason: String?,
    val result: AdminAuditResult,
    val httpStatus: Int,
    val requestId: String?,
    val clientIp: String?,
)
