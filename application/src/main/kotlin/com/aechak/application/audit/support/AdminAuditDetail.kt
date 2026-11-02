package com.aechak.application.audit.support

import com.aechak.domain.audit.auditlog.enums.AdminAuditTargetType

/**
 * 조치를 수행하는 경로가 감사 기록에 보태는 의미 — 대상과 사유, 그리고 기록 시점의 대상 이름.
 *
 * 조치를 가로채는 쪽(어드민 인터셉터)은 누가·언제·무슨 조치·결과·IP·추적 id까지만 알 수 있다.
 * 사유와 대상 이름은 요청 본문과 애그리거트에만 있어서, 그걸 아는 자리가 올려 준다.
 */
data class AdminAuditDetail(
    val targetType: AdminAuditTargetType? = null,
    val targetId: String? = null,
    val targetLabel: String? = null,
    val reason: String? = null,
)
