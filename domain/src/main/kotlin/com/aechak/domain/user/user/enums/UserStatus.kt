package com.aechak.domain.user.user.enums

import java.time.LocalDateTime

/**
 * 계정 생애주기와 운영 제재를 한 축에 둔다.
 *
 * 제재를 별도 축(별도 컬럼)으로 두지 않은 이유가 둘이다.
 * 하나, 접근 판정의 단일 지점이 매 요청 status 한 컬럼만 읽고 끝나야 한다 — 축이 둘이면 판정도 둘이 되고,
 * 한쪽만 보는 코드가 생기는 순간 제재가 조용히 새어 나간다.
 * 둘, 이용 제한·임시 정지·영구 정지는 서로 배타적이다 — 두 축이면 (ACTIVE + BANNED) 같은 모순 조합을 DB가 허용한다.
 *
 * 다만 WITHDRAWN(본인 의사)과 BANNED(운영 조치)는 차단 효과가 같아도 절대 합치지 않는다.
 * 한 값으로 묶으면 "이 계정이 스스로 떠났는지 쫓겨났는지"를 사후에 가릴 방법이 없다.
 */
enum class UserStatus {
    PENDING_ONBOARDING,
    ACTIVE,

    /** 이용 제한 — 로그인·조회·주문은 되고 커뮤니티성 쓰기(리뷰·SNS·문의)만 막힌다. */
    RESTRICTED,

    /** 임시 정지 — users.sanction_until 까지 전면 차단. 기간이 지나면 [effective] 가 ACTIVE 로 본다. */
    SUSPENDED,

    /** 영구 정지 — 전면 차단. 해제도 추가 제재도 없는 종단 상태. */
    BANNED,

    /** 탈퇴 — 본인 탈퇴와 운영자의 탈퇴 처리가 같은 값을 쓰고, 어느 쪽이었는지는 제재 이력이 가린다. */
    WITHDRAWN,
    ;

    companion object {
        /**
         * 접근 판정에 쓸 유효 상태. 기간이 지난 임시 정지는 ACTIVE 와 같다.
         *
         * 만료를 저장값 변경(배치)이 아니라 이 함수로 처리하는 이유: 인가 게이트와 어드민 조회가
         * 같은 함수를 공유해야 운영자가 화면에서 본 상태와 실제 접근 권한이 갈리지 않는다.
         * 저장값은 "7일 정지를 걸었다"는 사실 기록으로 그대로 남는다.
         */
        @JvmStatic
        fun effective(
            stored: UserStatus,
            sanctionUntil: LocalDateTime?,
            at: LocalDateTime,
        ): UserStatus = if (stored == SUSPENDED && sanctionUntil != null && !at.isBefore(sanctionUntil)) ACTIVE else stored
    }
}
