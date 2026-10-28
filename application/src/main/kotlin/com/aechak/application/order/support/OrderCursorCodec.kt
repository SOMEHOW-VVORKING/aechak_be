package com.aechak.application.order.support

import com.aechak.application.order.port.OrderStatusFilter
import com.aechak.common.error.BusinessException
import com.aechak.common.error.CommonErrorCode
import java.util.Base64

object OrderCursorCodec {
    private const val TAG = "o"
    private const val TOKEN_COUNT = 3

    data class DecodedCursor(
        val filter: OrderStatusFilter,
        val lastId: Long,
    )

    fun encode(
        filter: OrderStatusFilter,
        lastId: Long,
    ): String {
        val payload = "$TAG:${filter.name}:$lastId"
        return Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toByteArray(Charsets.UTF_8))
    }

    fun decode(raw: String): DecodedCursor {
        val parts = raw.decodeToPartsOrInvalid()
        if (parts.size != TOKEN_COUNT || parts[0] != TAG) throw BusinessException(CommonErrorCode.INVALID_CURSOR)
        return DecodedCursor(
            filter = parts[1].toFilterOrInvalid(),
            lastId = parts[2].toLongOrInvalid(),
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

    private fun String.toFilterOrInvalid(): OrderStatusFilter =
        OrderStatusFilter.entries.find { it.name == this } ?: throw BusinessException(CommonErrorCode.INVALID_CURSOR)

    private fun String.toLongOrInvalid(): Long = toLongOrNull() ?: throw BusinessException(CommonErrorCode.INVALID_CURSOR)
}
