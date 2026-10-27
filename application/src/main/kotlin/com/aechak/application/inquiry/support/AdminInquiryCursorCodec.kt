package com.aechak.application.inquiry.support

import com.aechak.application.inquiry.port.AdminInquiryAnchor
import com.aechak.common.error.BusinessException
import com.aechak.common.error.CommonErrorCode
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.Base64

/**
 * 운영자용 문의 목록 커서 코덱 — 정렬 키(접수 시각, id)를 그대로 싣는다.
 * 필터 조건은 커서에 넣지 않는다: 운영자가 필터를 바꿔도 같은 앵커에서 이어 읽히는 쪽이 자연스럽고,
 * 필터별 커서를 강제하면 "조건 바꾸면 커서 무효" 규칙을 프론트가 따라와야 한다.
 */
object AdminInquiryCursorCodec {
    private const val TAG = "ai"
    private const val SEPARATOR = ":"
    private const val FIELD_COUNT = 3

    // DB DATETIME(6) 정밀도에 맞춰 마이크로초로 보존한다 — 밀리초로 깎으면 동시각 행을 건너뛴다.
    private val CURSOR_EPOCH: LocalDateTime = LocalDateTime.of(1970, 1, 1, 0, 0)

    @JvmStatic
    fun encode(
        lastCreatedAt: LocalDateTime,
        lastInquiryId: Long,
    ): String {
        require(lastInquiryId > 0) { "lastInquiryId는 양수여야 합니다." }
        val micros = ChronoUnit.MICROS.between(CURSOR_EPOCH, lastCreatedAt.truncatedTo(ChronoUnit.MICROS))
        require(micros > 0) { "lastCreatedAt은 커서 기준 시각 이후여야 합니다." }
        return Base64
            .getUrlEncoder()
            .withoutPadding()
            .encodeToString(
                listOf(TAG, micros.toString(), lastInquiryId.toString())
                    .joinToString(SEPARATOR)
                    .toByteArray(Charsets.UTF_8),
            )
    }

    @JvmStatic
    fun decode(encodedCursor: String): AdminInquiryAnchor {
        val fields =
            try {
                String(Base64.getUrlDecoder().decode(encodedCursor), Charsets.UTF_8).split(SEPARATOR)
            } catch (e: IllegalArgumentException) {
                throw BusinessException(CommonErrorCode.INVALID_CURSOR, e)
            }
        if (fields.size != FIELD_COUNT || fields[0] != TAG) {
            invalidCursor()
        }
        return AdminInquiryAnchor(
            lastCreatedAt = CURSOR_EPOCH.plus(fields[1].toPositiveLongOrInvalid(), ChronoUnit.MICROS),
            lastInquiryId = fields[2].toPositiveLongOrInvalid(),
        )
    }

    private fun String.toPositiveLongOrInvalid(): Long = toLongOrNull()?.takeIf { it > 0 } ?: invalidCursor()

    private fun invalidCursor(): Nothing = throw BusinessException(CommonErrorCode.INVALID_CURSOR)
}
