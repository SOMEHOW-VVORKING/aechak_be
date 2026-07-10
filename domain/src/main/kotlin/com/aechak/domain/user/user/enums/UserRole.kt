package com.aechak.domain.user.user.enums

/**
 * 인가 구분 전용 — 도메인 명칭 금지.
 * 셀러 여부는 sellers 행(ACTIVE) 존재로 판정한다(SELLER 값 재추가 금지).
 */
enum class UserRole {
    GENERAL,
    ADMIN,
}
