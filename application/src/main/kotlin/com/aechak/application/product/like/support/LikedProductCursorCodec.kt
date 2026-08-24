package com.aechak.application.product.like.support

import com.aechak.common.error.BusinessException
import com.aechak.common.error.CommonErrorCode
import java.util.Base64

/** 내 찜 목록 커서 코덱 */
object LikedProductCursorCodec {
    private const val TAG = "lk"

    fun encode(lastLikeId: Long): String =
        Base64
            .getUrlEncoder()
            .withoutPadding()
            .encodeToString("$TAG:$lastLikeId".toByteArray(Charsets.UTF_8))

    fun decode(raw: String): Long {
        val payload =
            try {
                String(Base64.getUrlDecoder().decode(raw), Charsets.UTF_8)
            } catch (e: IllegalArgumentException) {
                throw BusinessException(CommonErrorCode.INVALID_CURSOR, e)
            }
        val parts = payload.split(':')
        if (parts.size != 2 || parts[0] != TAG) {
            throw BusinessException(CommonErrorCode.INVALID_CURSOR)
        }
        return parts[1].toLongOrNull()?.takeIf { it > 0 }
            ?: throw BusinessException(CommonErrorCode.INVALID_CURSOR)
    }
}
