package com.aechak.application.order.admin.support

import com.aechak.application.order.admin.port.AdminOrderCursor
import com.aechak.common.error.BusinessException
import com.aechak.common.error.CommonErrorCode
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.Base64

/**
 * 어드민 주문 목록 커서. 앵커는 주문 시각 + 주문 id다 — 같은 시각에 들어온 두 주문이 갈리지 않으면
 * 한 건이 두 페이지에 나오거나 아예 건너뛰어진다.
 *
 * 필터를 커서에 싣지 않는다. 운영자가 상태·기간·셀러 여섯 축을 돌려 보는 화면이라 필터를 묶으면
 * 축 하나만 건드려도 커서가 거부돼 1페이지로 튕긴다 — 화면이 필터 변경 시 커서를 버리는 쪽이 싸다.
 */
object AdminOrderCursorCodec {
    private const val TAG = "ao"
    private const val SEPARATOR = ":"
    private const val TOKEN_COUNT = 3

    fun encode(cursor: AdminOrderCursor): String {
        val payload =
            listOf(TAG, cursor.lastOrderedAt.toEpochMicro().toString(), cursor.lastOrderId.toString())
                .joinToString(SEPARATOR)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toByteArray(Charsets.UTF_8))
    }

    fun decode(encodedCursor: String): AdminOrderCursor {
        val parts = encodedCursor.decodeToParts()
        if (parts.size != TOKEN_COUNT || parts[0] != TAG) invalidCursor()
        return AdminOrderCursor(
            lastOrderedAt = parts[1].toPositiveLongOrInvalid().toLocalDateTime(),
            lastOrderId = parts[2].toPositiveLongOrInvalid(),
        )
    }

    private fun String.decodeToParts(): List<String> =
        try {
            String(Base64.getUrlDecoder().decode(this), Charsets.UTF_8).split(SEPARATOR)
        } catch (e: IllegalArgumentException) {
            invalidCursor(e)
        }

    private fun String.toPositiveLongOrInvalid(): Long = toLongOrNull()?.takeIf { it > 0 } ?: invalidCursor()

    private val CURSOR_EPOCH: LocalDateTime = LocalDateTime.of(1970, 1, 1, 0, 0)

    /** DB DATETIME(6) 정밀도에 맞춰 마이크로초로 보존 — 밀리초로 접으면 같은 밀리초의 두 주문이 섞인다 */
    private fun LocalDateTime.toEpochMicro(): Long =
        ChronoUnit.MICROS
            .between(CURSOR_EPOCH, truncatedTo(ChronoUnit.MICROS))
            .also { require(it > 0) { "lastOrderedAt must be after the cursor epoch." } }

    private fun Long.toLocalDateTime(): LocalDateTime = CURSOR_EPOCH.plus(this, ChronoUnit.MICROS)

    private fun invalidCursor(cause: Throwable? = null): Nothing = throw BusinessException(CommonErrorCode.INVALID_CURSOR, cause)
}
