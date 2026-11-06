package com.aechak.application.sns.support

import com.aechak.common.error.BusinessException
import com.aechak.common.error.CommonErrorCode
import java.security.MessageDigest
import java.util.Base64

/**
 * 댓글 목록 keyset 커서. 페이로드는 {tag}:{lastRootId}:{scopeHash}를 base64url로 감싼 값이고
 * 범위 해시는 게시물 publicId로 만들어 다른 게시물의 커서를 거른다.
 */
object CommentCursorCodec {
    private const val TAG = "cm"
    private const val SEPARATOR = ":"

    fun encode(
        postPublicId: String,
        lastRootId: Long,
    ): String {
        require(lastRootId > 0) { "lastRootId must be positive." }
        val payload = listOf(TAG, lastRootId.toString(), scopeHash(postPublicId)).joinToString(SEPARATOR)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toByteArray(Charsets.UTF_8))
    }

    /** 마지막 최상위 댓글 id. 형식, 태그, 범위 해시가 요청과 다르면 INVALID_CURSOR */
    fun decode(
        encodedCursor: String,
        postPublicId: String,
    ): Long {
        val parts =
            try {
                String(Base64.getUrlDecoder().decode(encodedCursor), Charsets.UTF_8).split(SEPARATOR)
            } catch (e: IllegalArgumentException) {
                throw BusinessException(CommonErrorCode.INVALID_CURSOR, e)
            }
        if (parts.size != 3 || parts[0] != TAG || parts[2] != scopeHash(postPublicId)) invalidCursor()
        return parts[1].toLongOrNull()?.takeIf { it > 0 } ?: invalidCursor()
    }

    private fun scopeHash(postPublicId: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest("$postPublicId\u001F$TAG".toByteArray(Charsets.UTF_8))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    private fun invalidCursor(): Nothing = throw BusinessException(CommonErrorCode.INVALID_CURSOR)
}
