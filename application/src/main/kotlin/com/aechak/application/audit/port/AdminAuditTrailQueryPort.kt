package com.aechak.application.audit.port

import com.aechak.application.audit.port.view.AdminAuditLogRowView
import java.time.LocalDateTime

/** 감사 로그 조회 — 읽기만 있다. 쓰기는 도메인 포트(AdminAuditLogRepository.append)뿐이다 */
interface AdminAuditTrailQueryPort {
    /** 조건에 맞는 행을 id 내림차순으로 lastId 다음부터 limit개 */
    fun findPage(
        condition: AdminAuditLogCondition,
        lastId: Long?,
        limit: Int,
    ): List<AdminAuditLogRowView>

    fun count(condition: AdminAuditLogCondition): Long

    /**
     * 기록이 시작된 시점 — **조건과 무관한** 전체 최소 acted_at. 행이 없으면 null.
     *
     * 소급 기록이 불가능하다는 사실을 화면이 알아야 해서 내려준다. 설정값이 아니라 데이터에서
     * 뽑는 이유는 환경마다 적용 시점이 다르고 설정은 낡기 때문이다. 보존 정리로 오래된 행이
     * 지워지면 이 값은 앞이 아니라 **뒤로** 움직이므로, "이 시각 이전은 믿지 말라"는 주장은
     * 언제나 안전한 쪽으로만 틀린다.
     */
    fun findRecordingSince(): LocalDateTime?
}
