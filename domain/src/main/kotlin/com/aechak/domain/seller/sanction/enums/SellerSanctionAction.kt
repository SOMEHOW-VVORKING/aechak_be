package com.aechak.domain.seller.sanction.enums

/** 판매정지 이력 한 행이 기록하는 운영 조치. 이력은 추가 전용이라 해제도 조치 하나로 남는다. */
enum class SellerSanctionAction {
    /** 판매정지 — 셀러 상태를 SUSPENDED로 옮긴다 */
    SUSPEND,

    /** 판매정지 해제 — 정지 직전 상태로 되돌린다 */
    RELEASE,
}
