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
 * 정렬 키는 id 하나다 — **기록 순서**로 읽는다. 늦게 들어온 행은 늘 더 큰 id를 받으므로
 * 내려가며 읽는 중에 건너뛰어질 수 없다. acted_at 으로 정렬하면 그 보장이 사라진다.
 */
object AdminAuditCursorCodec {
    private const val SEPARATOR = ":"

    // 지정하지 않은 필터 표시. 길이는 숫자라 숫자가 아닌 값이면 어느 길이와도 섞이지 않는다
    private const val UNSET_FIELD = "-"

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

    /**
     * 필터 전부를 한 문자열로 펴서 해시한다. 칸마다 **길이를 앞세우고 지정 안 함은 따로 표시한다.**
     *
     * 구분자만으로 이으면 검색어에 구분자를 넣어 칸 경계를 옮길 수 있고, 지정 안 함과 빈 문자열을
     * 같은 칸으로 접으면 조건이 실제로 다른 요청(SQL에서 전자는 필터 없음, 후자는 `= ''`)이
     * 같은 해시를 내 남의 커서가 통과한다.
     */
    private fun scopeHash(condition: AdminAuditLogCondition): String {
        val canonical =
            listOf(
                condition.actorPublicId,
                condition.targetType?.name,
                condition.action?.name,
                condition.result?.name,
                condition.from?.toString(),
                condition.to?.toString(),
                condition.keyword,
            ).joinToString(CANONICAL_SEPARATOR, transform = ::canonicalField)
        val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    /** 지정 안 함은 숫자가 아닌 표시로, 값은 `길이+구분자+값`으로 적는다 — 어느 쪽도 다른 칸의 경계를 옮기지 못한다 */
    private fun canonicalField(value: String?): String {
        if (value == null) return UNSET_FIELD
        return "${value.length}$CANONICAL_SEPARATOR$value"
    }

    private fun invalidCursor(): Nothing = throw BusinessException(CommonErrorCode.INVALID_CURSOR)
}
