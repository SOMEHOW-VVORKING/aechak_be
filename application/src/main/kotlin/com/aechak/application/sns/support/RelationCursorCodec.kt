package com.aechak.application.sns.support

import com.aechak.application.sns.port.SnsRelationKind
import com.aechak.common.error.BusinessException
import com.aechak.common.error.CommonErrorCode
import java.security.MessageDigest
import java.util.Base64

/**
 * 차단, 뮤트 관리 목록 keyset 커서.
 * 페이로드는 {tag}:{lastRelationId}:{scopeHash}를 base64url로 감싼 값이고 사용자 id는 싣지 않는다.
 */
object RelationCursorCodec {
    private const val SEPARATOR = ":"

    // 해시 입력 필드 구분자 (제어 문자 U+001F)
    private const val CANONICAL_SEPARATOR = "\u001F"

    fun encode(
        kind: SnsRelationKind,
        ownerPublicId: String,
        lastRelationId: Long,
    ): String {
        require(lastRelationId > 0) { "lastRelationId must be positive." }
        val payload = listOf(kind.tag(), lastRelationId.toString(), scopeHash(kind, ownerPublicId)).joinToString(SEPARATOR)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toByteArray(Charsets.UTF_8))
    }

    /** 마지막 관계 행 id. 형식, 태그, 범위 해시가 요청과 다르면 INVALID_CURSOR */
    fun decode(
        encodedCursor: String,
        kind: SnsRelationKind,
        ownerPublicId: String,
    ): Long {
        val parts =
            try {
                String(Base64.getUrlDecoder().decode(encodedCursor), Charsets.UTF_8).split(SEPARATOR)
            } catch (e: IllegalArgumentException) {
                throw BusinessException(CommonErrorCode.INVALID_CURSOR, e)
            }
        if (parts.size != 3 || parts[0] != kind.tag() || parts[2] != scopeHash(kind, ownerPublicId)) invalidCursor()
        return parts[1].toLongOrNull()?.takeIf { it > 0 } ?: invalidCursor()
    }

    private fun scopeHash(
        kind: SnsRelationKind,
        ownerPublicId: String,
    ): String {
        val canonical = listOf(ownerPublicId, kind.tag()).joinToString(CANONICAL_SEPARATOR)
        val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    private fun SnsRelationKind.tag(): String =
        when (this) {
            SnsRelationKind.BLOCK -> "bk"
            SnsRelationKind.MUTE -> "mt"
        }

    private fun invalidCursor(): Nothing = throw BusinessException(CommonErrorCode.INVALID_CURSOR)
}
