package com.aechak.application.product.search.support

import com.aechak.common.error.BusinessException
import com.aechak.common.error.CommonErrorCode
import java.util.Base64

/**
 * 키워드 상품 검색 커서 코덱
 * 검색어를 함께 저장해 다른 검색에서의 커서 재사용 차단
 */
object ProductKeywordSearchCursorCodec {
    private const val LATEST_TAG = "l"

    data class DecodedCursor(
        val keyword: String,
        val publicId: String,
    )

    fun encode(
        keyword: String,
        publicId: String,
    ): String {
        val keywordToken = keyword.toByteArray(Charsets.UTF_8).toBase64Url()
        val payload = "$LATEST_TAG:$keywordToken:$publicId"
        return payload.toByteArray(Charsets.UTF_8).toBase64Url()
    }

    fun decode(raw: String): DecodedCursor {
        val payload = String(raw.decodeBase64UrlOrInvalid(), Charsets.UTF_8)
        val parts = payload.split(':')
        if (parts.size != 3 || parts[0] != LATEST_TAG) {
            throw BusinessException(CommonErrorCode.INVALID_CURSOR)
        }
        return DecodedCursor(
            keyword = String(parts[1].decodeBase64UrlOrInvalid(), Charsets.UTF_8),
            publicId = parts[2].takeIf { it.isNotBlank() } ?: throw BusinessException(CommonErrorCode.INVALID_CURSOR),
        )
    }

    private fun ByteArray.toBase64Url(): String = Base64.getUrlEncoder().withoutPadding().encodeToString(this)

    private fun String.decodeBase64UrlOrInvalid(): ByteArray =
        try {
            Base64.getUrlDecoder().decode(this)
        } catch (e: IllegalArgumentException) {
            throw BusinessException(CommonErrorCode.INVALID_CURSOR, e)
        }
}
