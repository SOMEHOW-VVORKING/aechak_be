package com.aechak.application.user.sanction.support

import com.aechak.application.user.sanction.usecase.query.MemberSearchQuery
import com.aechak.common.error.BusinessException
import com.aechak.common.error.CommonErrorCode
import java.security.MessageDigest
import java.util.Base64

/**
 * 회원 목록 커서. 정렬은 가입 역순(id 내림차순)으로 고정이라 앵커는 마지막 id 하나로 충분하다.
 * 조건 지문을 커서에 박아 두는 이유: 조건이 바뀌면 같은 id 앵커가 다른 집합을 가리켜
 * 두 집합이 한 목록에 섞이거나 사이 구간이 통째로 빠진다. 바뀐 요청은 400으로 끊고 처음부터 다시 받게 한다.
 */
object MemberCursorCodec {
    private const val TAG = "m"
    private const val TOKEN_COUNT = 3
    private const val DIGEST_LENGTH = 16

    private val DIGEST_FORMAT = Regex("[A-Za-z0-9_-]{$DIGEST_LENGTH}")

    data class DecodedCursor(
        val filterDigest: String,
        val lastUserId: Long,
    )

    fun encode(
        query: MemberSearchQuery,
        lastUserId: Long,
    ): String {
        val payload = "$TAG:${digestOf(query)}:$lastUserId"
        return Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toByteArray(Charsets.UTF_8))
    }

    fun decode(raw: String): DecodedCursor {
        val parts = raw.decodeToPartsOrInvalid()
        if (parts.size != TOKEN_COUNT || parts[0] != TAG) throw BusinessException(CommonErrorCode.INVALID_CURSOR)
        return DecodedCursor(
            filterDigest = parts[1].toDigestOrInvalid(),
            lastUserId = parts[2].toLongOrInvalid(),
        )
    }

    /** 결과 집합을 바꾸는 조건 전부의 지문 — size·cursor는 집합을 바꾸지 않아 넣지 않는다. */
    fun digestOf(query: MemberSearchQuery): String {
        // 구분자를 키워드에 들어갈 수 없는 문자로 둬 "a|b"와 "a" + "|b"가 같은 지문이 되지 않게 한다
        val identity =
            listOf(
                query.statusFilter.name,
                query.keyword.orEmpty(),
                query.joinedFrom?.toString().orEmpty(),
                query.joinedTo?.toString().orEmpty(),
            ).joinToString("\u0000")
        val hash = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray(Charsets.UTF_8))
        return Base64
            .getUrlEncoder()
            .withoutPadding()
            .encodeToString(hash)
            .take(DIGEST_LENGTH)
    }

    private fun String.decodeToPartsOrInvalid(): List<String> {
        val payload =
            try {
                String(Base64.getUrlDecoder().decode(this), Charsets.UTF_8)
            } catch (e: IllegalArgumentException) {
                throw BusinessException(CommonErrorCode.INVALID_CURSOR, e)
            }
        return payload.split(':')
    }

    private fun String.toDigestOrInvalid(): String =
        takeIf { DIGEST_FORMAT.matches(it) } ?: throw BusinessException(CommonErrorCode.INVALID_CURSOR)

    private fun String.toLongOrInvalid(): Long = toLongOrNull() ?: throw BusinessException(CommonErrorCode.INVALID_CURSOR)
}
