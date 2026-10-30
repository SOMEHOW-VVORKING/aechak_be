package com.aechak.application.audit.service

import com.aechak.application.audit.port.AdminAuditLogCondition
import com.aechak.application.audit.port.AdminAuditTrailQueryPort
import com.aechak.application.audit.port.view.AdminAuditLogRowView
import com.aechak.application.audit.support.AdminAuditCursorCodec
import com.aechak.application.audit.usecase.query.AdminAuditLogListQuery
import com.aechak.application.support.CursorPageResult
import com.aechak.application.support.CursorPageSize
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class AdminAuditLogQueryService(
    private val auditTrailQueryPort: AdminAuditTrailQueryPort,
) {
    /** 첫 페이지에만 totalCount를 싣는다(SNS 관리 목록 선례). 커서 이후 페이지는 null */
    fun getPage(query: AdminAuditLogListQuery): CursorPageResult<AdminAuditLogRowView> {
        val condition = query.toCondition()
        val lastId = query.cursor?.let { AdminAuditCursorCodec.decode(it, condition) }
        val fetched = auditTrailQueryPort.findPage(condition, lastId, CursorPageSize.fetchLimit(query.size))
        return CursorPageResult.of(
            fetched = fetched,
            size = query.size,
            totalCount = if (query.cursor == null) auditTrailQueryPort.count(condition) else null,
        ) { last -> AdminAuditCursorCodec.encode(condition, last.id) }
    }

    fun findRecordingSince(): LocalDateTime? = auditTrailQueryPort.findRecordingSince()

    private fun AdminAuditLogListQuery.toCondition(): AdminAuditLogCondition =
        AdminAuditLogCondition(
            actorPublicId = actorPublicId,
            targetType = targetType,
            action = action,
            result = result,
            from = from,
            to = to,
            keyword = keyword,
        )
}
