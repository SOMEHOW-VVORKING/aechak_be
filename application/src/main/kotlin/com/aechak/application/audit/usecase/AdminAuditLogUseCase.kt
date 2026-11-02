package com.aechak.application.audit.usecase

import com.aechak.application.audit.usecase.command.RecordAdminActionCommand
import com.aechak.application.audit.usecase.query.AdminAuditLogListQuery
import com.aechak.application.audit.usecase.result.AdminAuditLogListResult

/**
 * 감사 로그 — 더하기와 읽기만 있다. 고치기·지우기 유스케이스를 만들지 않는 것이 이 계약의 요지다.
 */
interface AdminAuditLogUseCase {
    /**
     * 조치 한 건을 기록한다. **던지지 않는다** — 기록 실패가 이미 끝난 조치를 되돌리면 운영이 멈춘다.
     * 삼킨 실패는 ERROR 로그로 남겨 사람이 복원할 수 있게 한다.
     */
    fun record(command: RecordAdminActionCommand)

    fun list(query: AdminAuditLogListQuery): AdminAuditLogListResult
}
