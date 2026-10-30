package com.aechak.application.notice.read.support

import com.aechak.application.notice.read.port.NoticeReadAnchor
import com.aechak.common.error.BusinessException
import com.aechak.common.error.CommonErrorCode
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.Base64

/**
 * 독자용 공지 목록 커서 코덱 — 정렬 키(고정 여부, 게시 시각, id)를 그대로 싣는다.
 *
 * 고정 여부를 커서에 넣는 이유: 정렬이 pinned를 첫 키로 쓰기 때문이다. 빼면 고정 공지 묶음의
 * 마지막 행에서 다음 페이지를 열 때 게시 시각 비교만 남아, 고정 공지보다 최근에 게시된 일반 공지가
 * 전부 건너뛰어진다.
 */
object NoticeReadCursorCodec {
    private const val TAG = "nr"
    private const val SEPARATOR = ":"
    private const val FIELD_COUNT = 4
    private const val PINNED_TRUE = "1"
    private const val PINNED_FALSE = "0"

    // DB DATETIME(6) 정밀도에 맞춰 마이크로초로 보존한다 — 밀리초로 깎으면 동시각 행을 건너뛴다.
    private val CURSOR_EPOCH: LocalDateTime = LocalDateTime.of(1970, 1, 1, 0, 0)

    // MySQL DATETIME의 상한. 양수 검사만 하면 LocalDateTime이 담을 수 있는 먼 미래가 통과하는데,
    // 그 시각을 조회로 흘려보내면 MySQL이 ERROR 1525로 끊어 400이 아니라 500이 된다.
    private val CURSOR_MAX: LocalDateTime = LocalDateTime.of(9999, 12, 31, 23, 59, 59, 999_999_000)
    private val MAX_MICROS: Long = ChronoUnit.MICROS.between(CURSOR_EPOCH, CURSOR_MAX)

    fun encode(
        lastPinned: Boolean,
        lastPublishedAt: LocalDateTime,
        lastNoticeId: Long,
    ): String {
        require(lastNoticeId > 0) { "lastNoticeId는 양수여야 합니다." }
        val micros = ChronoUnit.MICROS.between(CURSOR_EPOCH, lastPublishedAt.truncatedTo(ChronoUnit.MICROS))
        require(micros > 0) { "lastPublishedAt은 커서 기준 시각 이후여야 합니다." }
        return Base64
            .getUrlEncoder()
            .withoutPadding()
            .encodeToString(
                listOf(
                    TAG,
                    if (lastPinned) PINNED_TRUE else PINNED_FALSE,
                    micros.toString(),
                    lastNoticeId.toString(),
                ).joinToString(SEPARATOR).toByteArray(Charsets.UTF_8),
            )
    }

    fun decode(encodedCursor: String): NoticeReadAnchor {
        val fields =
            try {
                String(Base64.getUrlDecoder().decode(encodedCursor), Charsets.UTF_8).split(SEPARATOR)
            } catch (e: IllegalArgumentException) {
                throw BusinessException(CommonErrorCode.INVALID_CURSOR, e)
            }
        if (fields.size != FIELD_COUNT || fields[0] != TAG) {
            invalidCursor()
        }
        return NoticeReadAnchor(
            lastPinned = fields[1].toPinnedOrInvalid(),
            lastPublishedAt = CURSOR_EPOCH.plus(fields[2].toMicrosOrInvalid(), ChronoUnit.MICROS),
            lastNoticeId = fields[3].toPositiveLongOrInvalid(),
        )
    }

    /** 0·1 외의 값은 위조다 — toBoolean()은 아무 문자열이나 false로 받아 조용히 통과시킨다. */
    private fun String.toPinnedOrInvalid(): Boolean =
        when (this) {
            PINNED_TRUE -> true
            PINNED_FALSE -> false
            else -> invalidCursor()
        }

    /** 시각 필드는 양수인 것으로 끝나지 않는다 — DB가 담을 수 있는 범위까지 좁혀야 조회로 새지 않는다. */
    private fun String.toMicrosOrInvalid(): Long = toLongOrNull()?.takeIf { it in 1..MAX_MICROS } ?: invalidCursor()

    private fun String.toPositiveLongOrInvalid(): Long = toLongOrNull()?.takeIf { it > 0 } ?: invalidCursor()

    private fun invalidCursor(): Nothing = throw BusinessException(CommonErrorCode.INVALID_CURSOR)
}
