package com.aechak.domain.payment.reconciliation

enum class ReconciliationResolution {
    /** 일치라 할 일이 없음 */
    NOT_REQUIRED,

    /** 자동 보정 대상으로 판정됐고 아직 실행 전 */
    AUTO_PENDING,

    /** 확정 본체를 태워 보정이 끝남 */
    AUTO_CONFIRMED,

    /** 돈이 걸린 판단이라 사람이 본다 */
    MANUAL_REQUIRED,

    /** 자동 보정을 시도했으나 실패 — 다음 회차가 이월해 다시 본다 */
    FAILED,

    /** 뒤 회차가 같은 결제를 다시 판정해 이 건은 닫힘. note에 대체한 회차를 적는다 */
    SUPERSEDED,
    ;

    /**
     * 아직 끝나지 않은 상태 — 이월 재판정 대상. 보정 대기(AUTO_PENDING)도 포함한다:
     * 회차가 보정 전에 죽으면 그 건은 창 밖으로 밀려 목록에 다시 안 잡히므로, 이월만이 되살릴 길이다.
     */
    val isOpen: Boolean get() = this == MANUAL_REQUIRED || this == FAILED || this == AUTO_PENDING
}
