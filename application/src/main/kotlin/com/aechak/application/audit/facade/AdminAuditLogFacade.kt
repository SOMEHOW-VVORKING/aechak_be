package com.aechak.application.audit.facade

import com.aechak.application.audit.service.AdminAuditLogAppender
import com.aechak.application.audit.service.AdminAuditLogQueryService
import com.aechak.application.audit.usecase.AdminAuditLogUseCase
import com.aechak.application.audit.usecase.command.RecordAdminActionCommand
import com.aechak.application.audit.usecase.query.AdminAuditLogListQuery
import com.aechak.application.audit.usecase.result.AdminAuditLogItemResult
import com.aechak.application.audit.usecase.result.AdminAuditLogListResult
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** AdminAuditLogUseCase의 유일한 구현체. 트랜잭션 경계는 여기 고정 */
@Service
class AdminAuditLogFacade(
    private val auditLogAppender: AdminAuditLogAppender,
    private val auditLogQueryService: AdminAuditLogQueryService,
) : AdminAuditLogUseCase {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 기록 실패를 여기서 삼킨다 — **고른 쪽과 근거**:
     *
     * 조치는 이미 커밋됐다. 여기서 예외를 올리면 어드민 응답이 500으로 바뀌어 운영자는 승인이
     * 실패한 줄 알고 다시 누른다(승격·개점처럼 두 번 누르면 충돌로 끊기는 조치가 섞여 있다).
     * 반대로 조용히 삼키면 빠진 줄을 아무도 모른다.
     *
     * 그래서 삼키되 조용히 두지 않는다 — 행을 사람이 손으로 복원할 수 있는 재료(운영자·조치·대상·
     * 사유·추적 id)를 ERROR 로그로 전부 남긴다. 추적 id가 같은 접근 로그와 맞추면 그 요청이
     * 무엇이었는지 복원된다. 조회 API가 내려주는 recordingSince는 구멍을 알려주지 못하므로
     * (빠진 행은 조회로 셀 수 없다) 이 로그가 유일한 경보 지점이다.
     *
     * Throwable까지 잡는다. 삼킬 범위를 좁히면 매핑 오류·커넥션 고갈 같은 Error 계열에서
     * 정확히 같은 사고(성공한 조치가 500으로 보임)가 난다.
     */
    override fun record(command: RecordAdminActionCommand) {
        try {
            auditLogAppender.append(command)
        } catch (e: Throwable) {
            log.error(
                "감사 로그 기록 실패, 수동 복원 대상 actorUserId={} action={} targetType={} targetId={} result={} requestId={} reason={}",
                command.actorUserId,
                command.action,
                command.targetType,
                command.targetId,
                command.result,
                command.requestId,
                command.reason,
                e,
            )
        }
    }

    @Transactional(readOnly = true)
    override fun list(query: AdminAuditLogListQuery): AdminAuditLogListResult =
        AdminAuditLogListResult(
            page = auditLogQueryService.getPage(query).map(AdminAuditLogItemResult::from),
            // 필터가 걸린 조회에서도 전체 기준으로 내려준다 — "조건에 안 걸렸다"와 "그 시점엔 기록이 없었다"가
            // 다른 말이고, 운영자가 구별해야 하는 쪽은 후자다
            recordingSince = if (query.cursor == null) auditLogQueryService.findRecordingSince() else null,
        )
}
