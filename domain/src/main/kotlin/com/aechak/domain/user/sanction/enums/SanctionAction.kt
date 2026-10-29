package com.aechak.domain.user.sanction.enums

/** 제재 이력 한 행이 기록하는 운영 조치. 화면의 네 가지 제재와 해제가 1:1로 대응한다. */
enum class SanctionAction {
    /** 이용 제한 */
    RESTRICT,

    /** 임시 정지 — 기간(effective_until)이 반드시 함께 온다 */
    SUSPEND,

    /** 영구 정지 */
    BAN,

    /** 탈퇴 처리 — 운영자가 내린 탈퇴. 본인 탈퇴와 상태값은 같고 이 이력 행이 둘을 가린다 */
    FORCE_WITHDRAW,

    /** 제재 해제 */
    RELEASE,
    ;

    /** 기간을 요구하는 조치인지 — 임시 정지만 해당한다. */
    fun requiresPeriod(): Boolean = this == SUSPEND
}
