package com.aechak.application.review.support

import com.aechak.application.review.port.ReviewListSort
import com.aechak.common.error.BusinessException
import com.aechak.common.error.CommonErrorCode
import java.util.Base64

/**
 * 리뷰 목록 커서 코덱
 */
object ReviewCursorCodec {
    private const val LATEST_TAG = "l"
    private const val RATING_TAG = "r"

    data class DecodedCursor(
        val productId: Long,
        val photoOnly: Boolean,
        val lastId: Long,
        val lastRating: Int?,
    )

    fun encode(
        sort: ReviewListSort,
        productId: Long,
        photoOnly: Boolean,
        lastId: Long,
        lastRating: Int,
    ): String {
        val photo = photoOnly.toFlag()
        val payload =
            when (sort) {
                ReviewListSort.LATEST -> "$LATEST_TAG:$productId:$photo:$lastId"
                ReviewListSort.RATING_DESC -> "$RATING_TAG:$productId:$photo:$lastRating:$lastId"
            }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toByteArray(Charsets.UTF_8))
    }

    fun decode(
        raw: String,
        sort: ReviewListSort,
    ): DecodedCursor {
        val parts = raw.decodeToPartsOrInvalid()
        return when (sort) {
            ReviewListSort.LATEST -> {
                parts.requireTagAndSize(LATEST_TAG, 4)
                DecodedCursor(
                    productId = parts[1].toLongOrInvalid(),
                    photoOnly = parts[2].toPhotoFlagOrInvalid(),
                    lastId = parts[3].toLongOrInvalid(),
                    lastRating = null,
                )
            }

            ReviewListSort.RATING_DESC -> {
                parts.requireTagAndSize(RATING_TAG, 5)
                DecodedCursor(
                    productId = parts[1].toLongOrInvalid(),
                    photoOnly = parts[2].toPhotoFlagOrInvalid(),
                    lastRating = parts[3].toIntOrInvalid(),
                    lastId = parts[4].toLongOrInvalid(),
                )
            }
        }
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

    private fun List<String>.requireTagAndSize(
        tag: String,
        size: Int,
    ) {
        if (this.size != size || this[0] != tag) throw BusinessException(CommonErrorCode.INVALID_CURSOR)
    }

    private fun Boolean.toFlag(): String = if (this) "1" else "0"

    private fun String.toPhotoFlagOrInvalid(): Boolean =
        when (this) {
            "1" -> true
            "0" -> false
            else -> throw BusinessException(CommonErrorCode.INVALID_CURSOR)
        }

    private fun String.toLongOrInvalid(): Long = toLongOrNull() ?: throw BusinessException(CommonErrorCode.INVALID_CURSOR)

    private fun String.toIntOrInvalid(): Int = toIntOrNull() ?: throw BusinessException(CommonErrorCode.INVALID_CURSOR)
}
