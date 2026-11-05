package com.aechak.application.audit.service

import com.aechak.application.audit.usecase.command.RecordAdminActionCommand
import com.aechak.domain.audit.auditlog.AdminAuditLog
import com.aechak.domain.audit.auditlog.repository.AdminAuditLogRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * 감사 행을 더하는 유일한 자리.
 *
 * **REQUIRES_NEW인 이유**: 이 쓰기가 조치의 트랜잭션에 올라타면, INSERT 하나가 실패하는 순간
 * 트랜잭션이 rollback-only로 표시돼 이미 성공한 조치(승인·승격)가 통째로 되돌아간다.
 * 자기 트랜잭션에서 커밋하고 실패도 자기 안에서 끝낸다.
 *
 * 삼키는 자리는 여기가 아니라 호출부([com.aechak.application.audit.facade.AdminAuditLogFacade])다 —
 * 프록시 안에서 잡으면 rollback-only 표시가 남아 커밋 시점에 다시 터진다.
 */
@Service
class AdminAuditLogAppender(
    private val auditLogRepository: AdminAuditLogRepository,
) {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun append(command: RecordAdminActionCommand): AdminAuditLog =
        auditLogRepository.append(
            AdminAuditLog.record(
                actorUserId = command.actorUserId,
                action = command.action,
                targetType = command.targetType,
                targetId = command.targetId,
                targetLabel = command.targetLabel,
                reason = command.reason,
                result = command.result,
                httpStatus = command.httpStatus,
                requestId = command.requestId,
                clientIp = command.clientIp,
                actedAt = command.actedAt,
            ),
        )
}
