package com.aechak.domain.audit.auditlog.enums

import com.aechak.domain.support.Ulid

/**
 * 감사 로그가 대상을 가리키는 식별자의 종류.
 *
 * 종류를 대상별로 선언하고 기록 시점에 대조한다 — 같은 대상 종류의 행이 어떤 날은 숫자,
 * 어떤 날은 ULID로 들어오면 그 테이블로는 "이 대상에게 누가 무슨 조치를 했나"를 물을 수 없다.
 */
enum class AdminAuditTargetIdKind {
    /** 공개 식별자(ULID 26자). 애그리거트가 publicId를 가진 대상은 모두 이것이다 */
    PUBLIC_ULID {
        override fun accepts(targetId: String): Boolean = targetId.length == Ulid.LENGTH && targetId.all { it in CROCKFORD_BASE32 }
    },

    /** 내부 bigint 식별자. publicId가 없는 애그리거트에만 허용한다 */
    INTERNAL_ID {
        override fun accepts(targetId: String): Boolean = (targetId.toLongOrNull() ?: 0L) > 0L
    },

    /**
     * `종류/내부id` 복합. 한 조치가 **표가 여러 개인 대상**을 다루고 표마다 id 시퀀스가 따로일 때 쓴다.
     *
     * 신고가 그렇다 — product_reports·review_reports·user_reports 가 각자 AUTO_INCREMENT 라
     * 숫자 하나로는 어느 행인지 가리지 못한다. 종류를 조치로 쪼개 대상 종류를 셋으로 나누는 길도
     * 있지만, 조치는 핸들러가 선언하는 값이라 한 핸들러가 세 종류를 받는 경로에서는 쓸 수 없다.
     *
     * 종류 칸은 열거 이름을 그대로 적는다(대문자·밑줄). 그래야 화면 필터가 문자열을 가공하지 않고
     * 앞자리로 가를 수 있다.
     */
    TYPED_INTERNAL_ID {
        override fun accepts(targetId: String): Boolean {
            val parts = targetId.split('/')
            if (parts.size != 2) return false
            val (kind, id) = parts
            return kind.isNotBlank() &&
                kind.all { it in 'A'..'Z' || it == '_' } &&
                (id.toLongOrNull() ?: 0L) > 0L
        }
    },
    ;

    abstract fun accepts(targetId: String): Boolean

    private companion object {
        /** Crockford Base32 — I·L·O·U를 뺀 32자. ulid-creator의 출력은 대문자다 */
        const val CROCKFORD_BASE32 = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
    }
}
