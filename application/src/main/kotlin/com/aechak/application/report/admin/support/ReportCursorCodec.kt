package com.aechak.application.report.admin.support

import com.aechak.application.report.admin.usecase.query.ReportSearchQuery
import com.aechak.common.error.BusinessException
import com.aechak.common.error.CommonErrorCode
import com.aechak.domain.report.decision.enums.ReportTargetType
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.format.DateTimeParseException
import java.util.Base64

/**
 * 신고 목록 커서. 앵커는 접수 시각·대상 종류·신고 id 세 축이고 조건 지문을 함께 담는다.
 * 조건이 바뀐 채 보낸 커서는 400 으로 끊는다(MemberCursorCodec 과 같은 방식).
 */
object ReportCursorCodec {
    private const val TAG = "r"

    /** 지문 재료의 구분자. 열거형 이름과 ISO 날짜에는 '|' 가 들어가지 않는다 */
    private const val SEPARATOR = "|"
    private const val MIN_TOKEN_COUNT = 5
    private const val DIGEST_LENGTH = 16

    private val DIGEST_FORMAT = Regex("[A-Za-z0-9_-]{$DIGEST_LENGTH}")

    data class DecodedCursor(
        val filterDigest: String,
        val lastReportedAt: LocalDateTime,
        val lastTargetType: ReportTargetType,
        val lastReportId: Long,
    )

    fun encode(
        query: ReportSearchQuery,
        lastReportedAt: LocalDateTime,
        lastTargetType: ReportTargetType,
        lastReportId: Long,
    ): String {
        val payload = "$TAG:${digestOf(query)}:$lastReportedAt:${lastTargetType.name}:$lastReportId"
        return Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toByteArray(Charsets.UTF_8))
    }

    fun decode(raw: String): DecodedCursor {
        val parts = raw.decodeToPartsOrInvalid()
        if (parts.size < MIN_TOKEN_COUNT || parts[0] != TAG) throw BusinessException(CommonErrorCode.INVALID_CURSOR)
        return DecodedCursor(
            filterDigest = parts[1].toDigestOrInvalid(),
            // 가운데 조각 전부가 시각이다 — ISO 표기의 ':' 때문에 둘 이상으로 쪼개져 있다
            lastReportedAt = parts.subList(2, parts.size - 2).joinToString(":").toDateTimeOrInvalid(),
            lastTargetType = parts[parts.size - 2].toTargetTypeOrInvalid(),
            lastReportId = parts[parts.size - 1].toLongOrInvalid(),
        )
    }

    /** 결과 집합을 바꾸는 조건 전부의 지문. size·cursor 는 집합을 바꾸지 않아 넣지 않는다. */
    fun digestOf(query: ReportSearchQuery): String {
        // 구분자는 값에 들어갈 수 없는 문자여야 한다 — 인접한 두 칸이 합쳐지면 지문이 같아진다
        val identity =
            listOf(
                query.targetType?.name.orEmpty(),
                query.status?.name.orEmpty(),
                query.reportedFrom?.toString().orEmpty(),
                query.reportedTo?.toString().orEmpty(),
            ).joinToString(SEPARATOR)
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

    private fun String.toTargetTypeOrInvalid(): ReportTargetType =
        ReportTargetType.entries.firstOrNull { it.name == this } ?: throw BusinessException(CommonErrorCode.INVALID_CURSOR)

    private fun String.toDateTimeOrInvalid(): LocalDateTime =
        try {
            LocalDateTime.parse(this)
        } catch (e: DateTimeParseException) {
            throw BusinessException(CommonErrorCode.INVALID_CURSOR, e)
        }
}
