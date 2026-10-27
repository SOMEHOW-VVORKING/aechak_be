package com.aechak.application.audit.support

import com.aechak.application.audit.port.AdminAuditLogCondition
import com.aechak.common.error.BusinessException
import com.aechak.common.error.CommonErrorCode
import java.security.MessageDigest
import java.util.Base64

/**
 * 감사 로그 목록 keyset 커서.
 *
 * 페이로드는 `{lastId}:{scopeHash}`를 base64url로 감싼 값이다. 범위 해시에 필터 전부를 넣어,
 * 필터를 바꾼 채 이전 커서를 쓰면 90002로 끊는다 — 다른 조건의 id로 이어 읽으면 빠진 구간을
 * 운영자가 알 수 없다.
 *
 * 정렬 키가 id 하나인 이유: 이 테이블은 더하기만 있어 id와 acted_at의 순서가 같다.
 * (acted_at, id) 복합 커서는 같은 결과에 깨질 자리만 늘린다.
 */
object AdminAuditCursorCodec {
    private const val SEPARATOR = ":"

    // 해시 입력 필드 구분자 (제어 문자 U+001F)
    private const val CANONICAL_SEPARATOR = ""

    fun encode(
        condition: AdminAuditLogCondition,
        lastId: Long,
    ): String {
        require(lastId > 0) { "lastId must be positive." }
        val payload = listOf(lastId.toString(), scopeHash(condition)).joinToString(SEPARATOR)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toByteArray(Charsets.UTF_8))
    }

    /** 마지막 행 id. 형식이나 범위 해시가 요청과 다르면 90002 */
    fun decode(
        encodedCursor: String,
        condition: AdminAuditLogCondition,
    ): Long {
        val parts =
            try {
                String(Base64.getUrlDecoder().decode(encodedCursor), Charsets.UTF_8).split(SEPARATOR)
            } catch (e: IllegalArgumentException) {
                throw BusinessException(CommonErrorCode.INVALID_CURSOR, e)
            }
        if (parts.size != 2 || parts[1] != scopeHash(condition)) invalidCursor()
        return parts[0].toLongOrNull()?.takeIf { it > 0 } ?: invalidCursor()
    }

    private fun scopeHash(condition: AdminAuditLogCondition): String {
        val canonical =
            listOf(
                condition.actorPublicId.orEmpty(),
                condition.targetType?.name.orEmpty(),
                condition.action?.name.orEmpty(),
                condition.result?.name.orEmpty(),
                condition.from?.toString().orEmpty(),
                condition.to?.toString().orEmpty(),
                condition.keyword.orEmpty(),
            ).joinToString(CANONICAL_SEPARATOR)
        val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    private fun invalidCursor(): Nothing = throw BusinessException(CommonErrorCode.INVALID_CURSOR)
}
