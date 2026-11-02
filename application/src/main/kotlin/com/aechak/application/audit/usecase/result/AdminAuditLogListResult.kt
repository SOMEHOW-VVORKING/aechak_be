package com.aechak.application.audit.usecase.result

import com.aechak.application.support.CursorPageResult
import java.time.LocalDateTime

/**
 * 감사 로그 목록 응답.
 *
 * [recordingSince]는 **첫 페이지에만** 싣는다(커서가 없을 때). 소급 기록이 불가능하므로
 * 화면이 "비어 있다"와 "아직 기록하지 않는다"를 구별할 수 있어야 한다. 행이 하나도 없으면 null이다.
 */
data class AdminAuditLogListResult(
    val page: CursorPageResult<AdminAuditLogItemResult>,
    val recordingSince: LocalDateTime?,
)
