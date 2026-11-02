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
    ;

    abstract fun accepts(targetId: String): Boolean

    private companion object {
        /** Crockford Base32 — I·L·O·U를 뺀 32자. ulid-creator의 출력은 대문자다 */
        const val CROCKFORD_BASE32 = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
    }
}
