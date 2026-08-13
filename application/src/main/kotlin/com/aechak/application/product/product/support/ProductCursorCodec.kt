package com.aechak.application.product.product.support

import com.aechak.application.product.product.port.ProductCatalogSort
import com.aechak.common.error.BusinessException
import com.aechak.common.error.CommonErrorCode
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Base64

/**
 * 상품 목록 커서 코덱.
 * base64url(패딩 없음)로 감싼 keyset 앵커. 정렬 태그와 카테고리 필터를 함께 실어, 다른 정렬이나 다른
 * 카테고리의 커서를 재사용하면 400으로 거절한다(필터 변경 시 keyset이 다른 집합에 걸려 생기는 조용한 오답 차단).
 * 위변조 방지 서명은 아니다.
 *
 * - LATEST: "l:{category-or-all}:{publicId}"
 * - PRICE_ASC: "p:{category-or-all}:{sortPriceAtAnchor}:{anchorEpochMillis}:{publicId}"
 */
object ProductCursorCodec {
    private const val LATEST_TAG = "l"
    private const val PRICE_ASC_TAG = "p"
    private const val ALL_CATEGORIES = "all"

    data class DecodedCursor(
        val categoryId: Long?,
        val publicId: String,
        val lastPrice: Long?,
        val anchorNow: LocalDateTime?,
    )

    fun encode(
        sort: ProductCatalogSort,
        categoryId: Long?,
        publicId: String,
        sortPriceAtAnchor: Long,
        now: LocalDateTime,
    ): String {
        val category = categoryId?.toString() ?: ALL_CATEGORIES
        val payload =
            when (sort) {
                ProductCatalogSort.LATEST -> "$LATEST_TAG:$category:$publicId"
                ProductCatalogSort.PRICE_ASC -> "$PRICE_ASC_TAG:$category:$sortPriceAtAnchor:${now.toEpochMillis()}:$publicId"
            }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toByteArray(Charsets.UTF_8))
    }

    fun decode(
        raw: String,
        sort: ProductCatalogSort,
    ): DecodedCursor {
        val payload =
            try {
                String(Base64.getUrlDecoder().decode(raw), Charsets.UTF_8)
            } catch (e: IllegalArgumentException) {
                throw BusinessException(CommonErrorCode.INVALID_CURSOR, e)
            }
        val parts = payload.split(':')
        return when (sort) {
            ProductCatalogSort.LATEST if parts.size == 3 && parts[0] == LATEST_TAG -> {
                DecodedCursor(
                    categoryId = parts[1].toCategoryTokenOrInvalid(),
                    publicId = parts[2].toPublicIdOrInvalid(),
                    lastPrice = null,
                    anchorNow = null,
                )
            }

            ProductCatalogSort.PRICE_ASC if parts.size == 5 && parts[0] == PRICE_ASC_TAG -> {
                DecodedCursor(
                    categoryId = parts[1].toCategoryTokenOrInvalid(),
                    publicId = parts[4].toPublicIdOrInvalid(),
                    lastPrice = parts[2].toNonNegativeLongOrInvalid(),
                    anchorNow = parts[3].toEpochMillisOrInvalid().toLocalDateTime(),
                )
            }

            // 태그·파트 수·정렬 불일치 전부 여기로 — 위조든 다른 정렬의 커서든 같은 400
            else -> {
                throw BusinessException(CommonErrorCode.INVALID_CURSOR)
            }
        }
    }

    // 직렬화 규약: LocalDateTime을 UTC 오프셋 기준 epoch millis로 왕복한다(양방향 일관이면 충분).
    private fun LocalDateTime.toEpochMillis(): Long = toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun Long.toLocalDateTime(): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(this), ZoneOffset.UTC)

    // "all"은 무필터(null), 그 외에는 양수 카테고리 id. 요청 categoryId와의 대조는 호출부(ProductService)가 수행한다.
    private fun String.toCategoryTokenOrInvalid(): Long? =
        if (this == ALL_CATEGORIES) {
            null
        } else {
            toLongOrNull()?.takeIf { it > 0 } ?: throw BusinessException(CommonErrorCode.INVALID_CURSOR)
        }

    private fun String.toPublicIdOrInvalid(): String = takeIf { it.isNotBlank() } ?: throw BusinessException(CommonErrorCode.INVALID_CURSOR)

    private fun String.toNonNegativeLongOrInvalid(): Long =
        toLongOrNull()?.takeIf { it >= 0 } ?: throw BusinessException(CommonErrorCode.INVALID_CURSOR)

    private fun String.toEpochMillisOrInvalid(): Long =
        toLongOrNull()?.takeIf { it > 0 } ?: throw BusinessException(CommonErrorCode.INVALID_CURSOR)
}
