package com.aechak.application.sns.support

import com.aechak.application.sns.port.SnsFollowDirection
import com.aechak.common.error.BusinessException
import com.aechak.common.error.CommonErrorCode
import java.security.MessageDigest
import java.util.Base64

/**
 * 팔로워, 팔로잉 목록 keyset 커서.
 * 페이로드는 {tag}:{lastFollowId}:{scopeHash}를 base64url로 감싼 값이고 사용자 id는 싣지 않는다.
 * 범위 해시는 목록 주인 publicId, 방향, 정규화한 검색어로 만든다.
 */
object FollowCursorCodec {
    private const val SEPARATOR = ":"

    // 해시 입력 필드 구분자 (제어 문자 U+001F)
    private const val CANONICAL_SEPARATOR = "\u001F"

    fun encode(
        direction: SnsFollowDirection,
        ownerPublicId: String,
        nickname: String?,
        lastFollowId: Long,
    ): String {
        require(lastFollowId > 0) { "lastFollowId must be positive." }
        val payload =
            listOf(direction.tag(), lastFollowId.toString(), scopeHash(direction, ownerPublicId, nickname)).joinToString(SEPARATOR)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toByteArray(Charsets.UTF_8))
    }

    /** 마지막 팔로우 행 id. 형식, 태그, 범위 해시가 요청과 다르면 INVALID_CURSOR */
    fun decode(
        encodedCursor: String,
        direction: SnsFollowDirection,
        ownerPublicId: String,
        nickname: String?,
    ): Long {
        val parts =
            try {
                String(Base64.getUrlDecoder().decode(encodedCursor), Charsets.UTF_8).split(SEPARATOR)
            } catch (e: IllegalArgumentException) {
                throw BusinessException(CommonErrorCode.INVALID_CURSOR, e)
            }
        if (parts.size != 3 || parts[0] != direction.tag() || parts[2] != scopeHash(direction, ownerPublicId, nickname)) {
            invalidCursor()
        }
        return parts[1].toLongOrNull()?.takeIf { it > 0 } ?: invalidCursor()
    }

    private fun scopeHash(
        direction: SnsFollowDirection,
        ownerPublicId: String,
        nickname: String?,
    ): String {
        // 검색어는 자유 텍스트라 길이 prefix로 구분자 충돌을 막는다
        val canonical =
            listOf(ownerPublicId, direction.tag(), nickname?.let { "${it.length}:$it" } ?: "").joinToString(CANONICAL_SEPARATOR)
        val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    private fun SnsFollowDirection.tag(): String =
        when (this) {
            SnsFollowDirection.FOLLOWERS -> "fw"
            SnsFollowDirection.FOLLOWINGS -> "fg"
        }

    private fun invalidCursor(): Nothing = throw BusinessException(CommonErrorCode.INVALID_CURSOR)
}
