package com.aechak.application.user.sanction.usecase.query

import com.aechak.domain.user.user.enums.UserStatus

/**
 * 어드민 회원 목록의 상태 필터. 어휘를 UserStatus 와 1:1로 두지 않고 별 이름으로 받는 이유는
 * 화면이 쓰는 어휘(정상·이용 제한·임시 정지·영구 정지·탈퇴 처리)가 저장 상태와 범위가 다르기 때문이다 —
 * NORMAL 은 온보딩 미완료까지 포함하고, ALL 은 전부를 뜻한다.
 */
enum class MemberStatusFilter {
    ALL,

    /** 제재가 없는 계정 — 온보딩 전 계정도 "제재 없음"이라 여기에 든다. */
    NORMAL,
    RESTRICTED,
    SUSPENDED,
    BANNED,
    WITHDRAWN,
    ;

    /** 이 필터가 포함하는 저장 상태. ALL 은 빈 목록(= 조건 없음)이다. */
    fun storedStatuses(): List<UserStatus> =
        when (this) {
            ALL -> emptyList()
            NORMAL -> listOf(UserStatus.ACTIVE, UserStatus.PENDING_ONBOARDING)
            RESTRICTED -> listOf(UserStatus.RESTRICTED)
            SUSPENDED -> listOf(UserStatus.SUSPENDED)
            BANNED -> listOf(UserStatus.BANNED)
            WITHDRAWN -> listOf(UserStatus.WITHDRAWN)
        }

    companion object {
        /** 알 수 없는 값은 400이 아니라 ALL 로 본다 — 목록 필터 오타가 화면을 깨뜨릴 이유가 없다. */
        @JvmStatic
        fun from(raw: String?): MemberStatusFilter = entries.find { it.name.equals(raw?.trim(), ignoreCase = true) } ?: ALL
    }
}
