package com.aechak.domain.audit.auditlog.enums

/** 조치의 처리 결과. 거부당한 시도도 남는다 — 누가 무엇을 하려 했는지가 책임 추적의 재료다 */
enum class AdminAuditResult {
    SUCCESS,
    FAILURE,
}
