package com.aechak.application.user.sanction.support

import com.aechak.application.user.sanction.usecase.query.MemberStatusFilter
import com.aechak.common.error.BusinessException
import com.aechak.common.error.CommonErrorCode
import java.util.Base64

/**
 * 회원 목록 커서. 정렬은 가입 역순(id 내림차순)으로 고정이라 앵커는 마지막 id 하나로 충분하다.
 * 상태 필터를 커서에 박아 두는 이유: 필터가 바뀌면 같은 id 앵커가 다른 집합을 가리켜
 * 두 집합이 한 목록에 섞인다. 바뀐 요청은 400으로 끊고 처음부터 다시 받게 한다.
 */
object MemberCursorCodec {
    private const val TAG = "m"
    private const val TOKEN_COUNT = 3

    data class DecodedCursor(
        val filter: MemberStatusFilter,
        val lastUserId: Long,
    )

    fun encode(
        filter: MemberStatusFilter,
        lastUserId: Long,
    ): String {
        val payload = "$TAG:${filter.name}:$lastUserId"
        return Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toByteArray(Charsets.UTF_8))
    }

    fun decode(raw: String): DecodedCursor {
        val parts = raw.decodeToPartsOrInvalid()
        if (parts.size != TOKEN_COUNT || parts[0] != TAG) throw BusinessException(CommonErrorCode.INVALID_CURSOR)
        return DecodedCursor(
            filter = parts[1].toFilterOrInvalid(),
            lastUserId = parts[2].toLongOrInvalid(),
        )
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

    private fun String.toFilterOrInvalid(): MemberStatusFilter =
        MemberStatusFilter.entries.find { it.name == this } ?: throw BusinessException(CommonErrorCode.INVALID_CURSOR)

    private fun String.toLongOrInvalid(): Long = toLongOrNull() ?: throw BusinessException(CommonErrorCode.INVALID_CURSOR)
}
