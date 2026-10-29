package com.aechak.application.order.claim.admin.support

import com.aechak.application.order.claim.admin.port.AdminClaimSort
import com.aechak.common.error.BusinessException
import com.aechak.common.error.CommonErrorCode
import java.util.Base64

/**
 * 어드민 클레임 목록 커서. 정렬축을 커서에 함께 담는다 — 담지 않으면 정렬을 바꾼 요청에
 * 이전 커서를 실어 보낼 수 있고, 그러면 부등호 방향이 뒤집혀 건너뛴 구간이 생긴다.
 */
object AdminClaimCursorCodec {
    private const val TAG = "ac"
    private const val TOKEN_COUNT = 3

    data class DecodedCursor(
        val sort: AdminClaimSort,
        val lastClaimId: Long,
    )

    fun encode(
        sort: AdminClaimSort,
        lastClaimId: Long,
    ): String {
        val payload = "$TAG:${sort.name}:$lastClaimId"
        return Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toByteArray(Charsets.UTF_8))
    }

    fun decode(raw: String): DecodedCursor {
        val parts = raw.decodeToPartsOrInvalid()
        if (parts.size != TOKEN_COUNT || parts[0] != TAG) throw BusinessException(CommonErrorCode.INVALID_CURSOR)
        return DecodedCursor(
            sort = parts[1].toSortOrInvalid(),
            lastClaimId = parts[2].toLongOrInvalid(),
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

    private fun String.toSortOrInvalid(): AdminClaimSort =
        AdminClaimSort.entries.find { it.name == this } ?: throw BusinessException(CommonErrorCode.INVALID_CURSOR)

    private fun String.toLongOrInvalid(): Long = toLongOrNull() ?: throw BusinessException(CommonErrorCode.INVALID_CURSOR)
}
