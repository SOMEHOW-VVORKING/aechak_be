package com.aechak.application.sns.support

import com.aechak.common.error.BusinessException
import com.aechak.common.error.CommonErrorCode
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.Base64

/**
 * 피드와 프로필 탭 목록의 keyset 커서.
 * 페이로드는 태그로 시작하는 `:` 구분 값을 base64url(패딩 없음)로 감싼 값이고 사용자 id는 싣지 않는다.
 * 시각은 오프셋 없는 LocalDateTime을 UTC 기준 epoch 마이크로초로 담는다.
 *  - 팔로잉 fl:{anchor}:{lastTime}:{lastPostPublicId}
 *  - 탐색 ex:{anchor}:{lastPostPublicId}
 *  - 인기 pp:{epochMinute}:{offset}
 */
object SnsFeedCursorCodec {
    private const val SEPARATOR = ":"
    private const val FOLLOWING_TAG = "fl"
    private const val EXPLORE_TAG = "ex"
    private const val POPULAR_TAG = "pp"

    /** 팔로잉 피드 커서. anchor는 첫 페이지의 기준 시각이고 lastTime은 마지막 카드의 정렬 시각이다 */
    data class FollowingCursor(
        val anchor: LocalDateTime,
        val lastTime: LocalDateTime,
        val lastPostPublicId: String,
    )

    fun encodeFollowing(
        anchor: LocalDateTime,
        lastTime: LocalDateTime,
        lastPostPublicId: String,
    ): String = encode(FOLLOWING_TAG, anchor.toEpochMicros().toString(), lastTime.toEpochMicros().toString(), lastPostPublicId)

    /** 형식이나 태그가 다르거나 두 시각 중 하나가 양수가 아니면 INVALID_CURSOR */
    fun decodeFollowing(cursor: String): FollowingCursor {
        val (anchor, lastTime, lastPostPublicId) = decode(cursor, FOLLOWING_TAG, 3)
        return FollowingCursor(anchor.toTimeOrInvalid(), lastTime.toTimeOrInvalid(), lastPostPublicId.toTokenOrInvalid())
    }

    /** 탐색 피드 커서. anchor는 첫 페이지의 기준 시각이다 */
    data class ExploreCursor(
        val anchor: LocalDateTime,
        val lastPostPublicId: String,
    )

    fun encodeExplore(
        anchor: LocalDateTime,
        lastPostPublicId: String,
    ): String = encode(EXPLORE_TAG, anchor.toEpochMicros().toString(), lastPostPublicId)

    /** 형식이나 태그가 다르거나 시각이 양수가 아니면 INVALID_CURSOR */
    fun decodeExplore(cursor: String): ExploreCursor {
        val (anchor, lastPostPublicId) = decode(cursor, EXPLORE_TAG, 2)
        return ExploreCursor(anchor.toTimeOrInvalid(), lastPostPublicId.toTokenOrInvalid())
    }

    /** 인기 피드 커서. epochMinute은 순위를 정한 분이고 offset은 그 순위에서 다음에 읽을 자리다 */
    data class PopularCursor(
        val epochMinute: Long,
        val offset: Int,
    )

    fun encodePopular(
        epochMinute: Long,
        offset: Int,
    ): String = encode(POPULAR_TAG, epochMinute.toString(), offset.toString())

    /** 형식이나 태그가 다르거나 분이 양수가 아니거나 자리가 순위 범위 밖이면 INVALID_CURSOR */
    fun decodePopular(cursor: String): PopularCursor {
        val (epochMinute, offset) = decode(cursor, POPULAR_TAG, 2)
        return PopularCursor(
            epochMinute = epochMinute.toLongOrNull()?.takeIf { it > 0 } ?: invalidCursor(),
            offset = offset.toIntOrNull()?.takeIf { it in 0 until PopularScore.MAX_RANK } ?: invalidCursor(),
        )
    }

    private fun encode(
        tag: String,
        vararg values: String,
    ): String =
        Base64
            .getUrlEncoder()
            .withoutPadding()
            .encodeToString((listOf(tag) + values).joinToString(SEPARATOR).toByteArray(Charsets.UTF_8))

    /** 태그 뒤의 값 목록. 태그나 값 개수가 다르면 INVALID_CURSOR */
    private fun decode(
        cursor: String,
        tag: String,
        valueCount: Int,
    ): List<String> {
        val parts =
            try {
                String(Base64.getUrlDecoder().decode(cursor), Charsets.UTF_8).split(SEPARATOR)
            } catch (e: IllegalArgumentException) {
                throw BusinessException(CommonErrorCode.INVALID_CURSOR, e)
            }
        if (parts.size != valueCount + 1 || parts[0] != tag) invalidCursor()
        return parts.drop(1)
    }

    private fun LocalDateTime.toEpochMicros(): Long = ChronoUnit.MICROS.between(Instant.EPOCH, toInstant(ZoneOffset.UTC))

    private fun String.toTimeOrInvalid(): LocalDateTime {
        val micros = toLongOrNull()?.takeIf { it > 0 } ?: invalidCursor()
        return LocalDateTime.ofInstant(Instant.EPOCH.plus(micros, ChronoUnit.MICROS), ZoneOffset.UTC)
    }

    private fun String.toTokenOrInvalid(): String = takeIf { it.isNotBlank() } ?: invalidCursor()

    private fun invalidCursor(): Nothing = throw BusinessException(CommonErrorCode.INVALID_CURSOR)
}
