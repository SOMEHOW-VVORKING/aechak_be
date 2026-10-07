package com.aechak.application.review.support

import com.aechak.application.review.port.UnreviewedOrderItemAnchor
import com.aechak.application.review.port.WrittenReviewAnchor
import com.aechak.common.error.BusinessException
import com.aechak.common.error.CommonErrorCode
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.Base64

object ReviewCursorCodec {
    object ProductReviews {
        private const val LATEST_TAG = "l"
        private const val RATING_DESC_TAG = "r"

        data class LatestAnchor(
            val lastReviewId: Long,
        )

        data class RatingDescAnchor(
            val lastRating: Int,
            val lastReviewId: Long,
        )

        fun encodeLatest(
            productId: Long,
            photoOnly: Boolean,
            lastReviewId: Long,
        ): String =
            CursorPayloadCodec.encode(
                LATEST_TAG,
                productId.requirePositive("productId").toString(),
                photoOnly.toFlag(),
                lastReviewId.requirePositive("lastReviewId").toString(),
            )

        fun encodeRatingDesc(
            productId: Long,
            photoOnly: Boolean,
            lastRating: Int,
            lastReviewId: Long,
        ): String =
            CursorPayloadCodec.encode(
                RATING_DESC_TAG,
                productId.requirePositive("productId").toString(),
                photoOnly.toFlag(),
                lastRating.requireRating().toString(),
                lastReviewId.requirePositive("lastReviewId").toString(),
            )

        fun decodeLatest(
            encodedCursor: String,
            expectedProductId: Long,
            expectedPhotoOnly: Boolean,
        ): LatestAnchor {
            val (_, productId, photoOnly, lastReviewId) =
                CursorPayloadCodec
                    .decode(encodedCursor)
                    .requireSchema(LATEST_TAG, size = 4)

            requireProductScope(productId, photoOnly, expectedProductId, expectedPhotoOnly)
            return LatestAnchor(lastReviewId = lastReviewId.toPositiveLongOrInvalid())
        }

        fun decodeRatingDesc(
            encodedCursor: String,
            expectedProductId: Long,
            expectedPhotoOnly: Boolean,
        ): RatingDescAnchor {
            val (_, productId, photoOnly, lastRating, lastReviewId) =
                CursorPayloadCodec
                    .decode(encodedCursor)
                    .requireSchema(RATING_DESC_TAG, size = 5)

            requireProductScope(productId, photoOnly, expectedProductId, expectedPhotoOnly)
            return RatingDescAnchor(
                lastRating = lastRating.toRatingOrInvalid(),
                lastReviewId = lastReviewId.toPositiveLongOrInvalid(),
            )
        }

        private fun requireProductScope(
            productId: String,
            photoOnly: String,
            expectedProductId: Long,
            expectedPhotoOnly: Boolean,
        ) {
            if (
                productId.toPositiveLongOrInvalid() != expectedProductId ||
                photoOnly.toFlagOrInvalid() != expectedPhotoOnly
            ) {
                invalidCursor()
            }
        }
    }

    object MyReviews {
        private const val WRITTEN_TAG = "mw"
        private const val UNREVIEWED_TAG = "mu"

        fun encodeWritten(
            userId: Long,
            lastReviewId: Long,
        ): String =
            CursorPayloadCodec.encode(
                WRITTEN_TAG,
                userId.requirePositive("userId").toString(),
                lastReviewId.requirePositive("lastReviewId").toString(),
            )

        fun encodeUnreviewedOrderItem(
            userId: Long,
            lastConfirmedAt: LocalDateTime,
            lastOrderItemId: Long,
        ): String =
            CursorPayloadCodec.encode(
                UNREVIEWED_TAG,
                userId.requirePositive("userId").toString(),
                lastConfirmedAt.toEpochMicro().toString(),
                lastOrderItemId.requirePositive("lastOrderItemId").toString(),
            )

        fun decodeWritten(
            encodedCursor: String,
            expectedUserId: Long,
        ): WrittenReviewAnchor {
            val (_, userId, lastReviewId) =
                CursorPayloadCodec
                    .decode(encodedCursor)
                    .requireSchema(WRITTEN_TAG, size = 3)

            requireSameUser(userId, expectedUserId)
            return WrittenReviewAnchor(lastReviewId = lastReviewId.toPositiveLongOrInvalid())
        }

        fun decodeUnreviewedOrderItem(
            encodedCursor: String,
            expectedUserId: Long,
        ): UnreviewedOrderItemAnchor {
            val (_, userId, lastConfirmedAt, lastOrderItemId) =
                CursorPayloadCodec
                    .decode(encodedCursor)
                    .requireSchema(UNREVIEWED_TAG, size = 4)

            requireSameUser(userId, expectedUserId)
            return UnreviewedOrderItemAnchor(
                lastConfirmedAt = lastConfirmedAt.toPositiveLongOrInvalid().toLocalDateTime(),
                lastOrderItemId = lastOrderItemId.toPositiveLongOrInvalid(),
            )
        }

        private fun requireSameUser(
            userId: String,
            expectedUserId: Long,
        ) {
            if (userId.toPositiveLongOrInvalid() != expectedUserId) invalidCursor()
        }
    }
}

private object CursorPayloadCodec {
    private const val SEPARATOR = ":"

    fun encode(vararg fields: String): String {
        require(fields.none { SEPARATOR in it }) { "커서 필드에는 구분자를 포함할 수 없습니다." }
        return Base64
            .getUrlEncoder()
            .withoutPadding()
            .encodeToString(fields.joinToString(SEPARATOR).toByteArray(Charsets.UTF_8))
    }

    fun decode(encodedCursor: String): List<String> =
        try {
            String(Base64.getUrlDecoder().decode(encodedCursor), Charsets.UTF_8).split(SEPARATOR)
        } catch (e: IllegalArgumentException) {
            invalidCursor(e)
        }
}

private fun List<String>.requireSchema(
    tag: String,
    size: Int,
): List<String> = takeIf { it.size == size && it.first() == tag } ?: invalidCursor()

private fun Boolean.toFlag(): String = if (this) "1" else "0"

private fun String.toFlagOrInvalid(): Boolean =
    when (this) {
        "1" -> true
        "0" -> false
        else -> invalidCursor()
    }

private fun String.toPositiveLongOrInvalid(): Long = toLongOrNull()?.takeIf { it > 0 } ?: invalidCursor()

private fun String.toRatingOrInvalid(): Int = toIntOrNull()?.takeIf { it in 1..5 } ?: invalidCursor()

private fun Long.requirePositive(name: String): Long = apply { require(this > 0) { "$name must be positive." } }

private fun Int.requireRating(): Int = apply { require(this in 1..5) { "lastRating must be between 1 and 5." } }

private val CURSOR_EPOCH: LocalDateTime = LocalDateTime.of(1970, 1, 1, 0, 0)

// DB LocalDateTime 정밀도에 맞춰 마이크로초 단위로 보존
private fun LocalDateTime.toEpochMicro(): Long =
    ChronoUnit.MICROS
        .between(CURSOR_EPOCH, truncatedTo(ChronoUnit.MICROS))
        .also { require(it > 0) { "lastConfirmedAt must be after the cursor epoch." } }

private fun Long.toLocalDateTime(): LocalDateTime = CURSOR_EPOCH.plus(this, ChronoUnit.MICROS)

private fun invalidCursor(cause: Throwable? = null): Nothing = throw BusinessException(CommonErrorCode.INVALID_CURSOR, cause)
