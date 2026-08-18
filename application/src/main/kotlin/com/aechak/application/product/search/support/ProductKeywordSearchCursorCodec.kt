package com.aechak.application.product.search.support

import com.aechak.application.product.search.port.ProductKeywordFilter
import com.aechak.application.product.search.port.ProductKeywordSearchSort
import com.aechak.common.error.BusinessException
import com.aechak.common.error.CommonErrorCode
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Base64

/**
 * 키워드 검색 keyset 커서의 encode/decode 코덱.
 *
 * 페이로드(base64url로 한 번 더 감쌈)
 *  - POPULAR    r:{filterHash}:{lastReviewCount}:{anchorEpochMillis}:{publicId}
 *  - PRICE_ASC  p:{filterHash}:{lastPrice}:{anchorEpochMillis}:{publicId}
 *  - LATEST     l:{filterHash}:{anchorEpochMillis}:{publicId}
 */
object ProductKeywordSearchCursorCodec {
    private const val POPULAR_TAG = "r"
    private const val PRICE_ASC_TAG = "p"
    private const val LATEST_TAG = "l"

    // 해시 입력 필드 구분자 (제어 문자 U+001F)
    private const val CANONICAL_SEPARATOR = "\u001F"

    /** 되읽은 커서 (lastReviewCount는 인기순, lastPrice는 가격순에서만 non-null) */
    data class DecodedCursor(
        val filterHash: String,
        val publicId: String,
        val lastReviewCount: Int?,
        val lastPrice: Long?,
        val anchorNow: LocalDateTime,
    )

    /** 필터를 대표하는 해시. 커서가 어떤 필터에서 나왔는지 식별하는 값 */
    fun filterHash(filter: ProductKeywordFilter): String {
        val canonical =
            listOf(
                // 자유 텍스트라 길이-prefix로 구분자 충돌 방지
                "${filter.keyword.length}:${filter.keyword}",
                filter.minPrice?.toString() ?: "",
                filter.maxPrice?.toString() ?: "",
                filter.minRating?.stripTrailingZeros()?.toPlainString() ?: "",
                filter.categoryId?.toString() ?: "",
                if (filter.freeShipping) "1" else "0",
                if (filter.excludeSoldOut) "1" else "0",
            ).joinToString(CANONICAL_SEPARATOR)
        val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    /** 정렬별 페이로드를 base64url로 감싼 다음 페이지 커서 생성 */
    fun encode(
        sort: ProductKeywordSearchSort,
        filterHash: String,
        publicId: String,
        lastReviewCount: Int?,
        lastPrice: Long?,
        now: LocalDateTime,
    ): String {
        val anchor = now.toEpochMillis()
        val payload =
            when (sort) {
                ProductKeywordSearchSort.POPULAR -> {
                    "$POPULAR_TAG:$filterHash:${requireNotNull(lastReviewCount)}:$anchor:$publicId"
                }

                ProductKeywordSearchSort.PRICE_ASC -> {
                    "$PRICE_ASC_TAG:$filterHash:${requireNotNull(lastPrice)}:$anchor:$publicId"
                }

                ProductKeywordSearchSort.LATEST -> {
                    "$LATEST_TAG:$filterHash:$anchor:$publicId"
                }
            }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toByteArray(Charsets.UTF_8))
    }

    /** 커서 되읽기. 형식이나 정렬 태그가 어긋나면 INVALID_CURSOR */
    fun decode(
        raw: String,
        sort: ProductKeywordSearchSort,
    ): DecodedCursor {
        val payload =
            try {
                String(Base64.getUrlDecoder().decode(raw), Charsets.UTF_8)
            } catch (e: IllegalArgumentException) {
                throw BusinessException(CommonErrorCode.INVALID_CURSOR, e)
            }
        val parts = payload.split(':')
        return when (sort) {
            ProductKeywordSearchSort.POPULAR if parts.size == 5 && parts[0] == POPULAR_TAG -> {
                val (_, hash, reviewCount, anchor, publicId) = parts
                DecodedCursor(
                    filterHash = hash.toTokenOrInvalid(),
                    publicId = publicId.toTokenOrInvalid(),
                    lastReviewCount = reviewCount.toNonNegativeIntOrInvalid(),
                    lastPrice = null,
                    anchorNow = anchor.toEpochMillisOrInvalid().toLocalDateTime(),
                )
            }

            ProductKeywordSearchSort.PRICE_ASC if parts.size == 5 && parts[0] == PRICE_ASC_TAG -> {
                val (_, hash, price, anchor, publicId) = parts
                DecodedCursor(
                    filterHash = hash.toTokenOrInvalid(),
                    publicId = publicId.toTokenOrInvalid(),
                    lastReviewCount = null,
                    lastPrice = price.toNonNegativeLongOrInvalid(),
                    anchorNow = anchor.toEpochMillisOrInvalid().toLocalDateTime(),
                )
            }

            ProductKeywordSearchSort.LATEST if parts.size == 4 && parts[0] == LATEST_TAG -> {
                val (_, hash, anchor, publicId) = parts
                DecodedCursor(
                    filterHash = hash.toTokenOrInvalid(),
                    publicId = publicId.toTokenOrInvalid(),
                    lastReviewCount = null,
                    lastPrice = null,
                    anchorNow = anchor.toEpochMillisOrInvalid().toLocalDateTime(),
                )
            }

            else -> {
                throw BusinessException(CommonErrorCode.INVALID_CURSOR)
            }
        }
    }

    private fun LocalDateTime.toEpochMillis(): Long = toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun Long.toLocalDateTime(): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(this), ZoneOffset.UTC)

    private fun String.toTokenOrInvalid(): String = takeIf { it.isNotBlank() } ?: throw BusinessException(CommonErrorCode.INVALID_CURSOR)

    private fun String.toNonNegativeIntOrInvalid(): Int =
        toIntOrNull()?.takeIf { it >= 0 } ?: throw BusinessException(CommonErrorCode.INVALID_CURSOR)

    private fun String.toNonNegativeLongOrInvalid(): Long =
        toLongOrNull()?.takeIf { it >= 0 } ?: throw BusinessException(CommonErrorCode.INVALID_CURSOR)

    private fun String.toEpochMillisOrInvalid(): Long =
        toLongOrNull()?.takeIf { it > 0 } ?: throw BusinessException(CommonErrorCode.INVALID_CURSOR)
}
