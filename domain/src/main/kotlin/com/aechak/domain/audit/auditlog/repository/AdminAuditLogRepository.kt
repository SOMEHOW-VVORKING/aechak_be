package com.aechak.domain.audit.auditlog.repository

import com.aechak.domain.audit.auditlog.AdminAuditLog

/**
 * 감사 로그 포트 — **더하기만 있다.**
 *
 * save·update·delete를 열지 않는 것이 의도다. 수정 경로가 하나라도 열리면 그 로그는
 * 분쟁에서 증거가 되지 못한다. 보존 기간 정리가 필요해지면 그때 별도 포트(운영 전용)로 분리하고,
 * 이 포트에 덧붙이지 않는다.
 */
interface AdminAuditLogRepository {
    fun append(log: AdminAuditLog): AdminAuditLog
}
