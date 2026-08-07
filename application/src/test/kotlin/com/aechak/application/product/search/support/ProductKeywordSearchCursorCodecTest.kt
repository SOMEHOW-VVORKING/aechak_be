package com.aechak.application.product.search.support

import com.aechak.common.error.BusinessException
import com.aechak.common.error.CommonErrorCode
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals

/** 계약 테스트 — 검색 커서의 인코딩 후 디코딩 시 값 보존과 위조·불일치 입력의 400 거절 고정 */
class ProductKeywordSearchCursorCodecTest {
    private val publicId = "01JABCDEFGHJKMNPQRSTVWXYZ0"

    private fun token(keyword: String): String = Base64.getUrlEncoder().withoutPadding().encodeToString(keyword.toByteArray())

    private fun crafted(payload: String): String = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toByteArray())

    private fun assertInvalidCursor(block: () -> Unit) {
        try {
            block()
            throw AssertionError("INVALID_CURSOR가 발생해야 한다")
        } catch (e: BusinessException) {
            assertEquals(CommonErrorCode.INVALID_CURSOR, e.errorCode)
        }
    }

    @Test
    fun `인코딩한 커서를 디코딩하면 검색어와 publicId가 유지된다`() {
        val decoded = ProductKeywordSearchCursorCodec.decode(ProductKeywordSearchCursorCodec.encode("사료", publicId))
        assertEquals("사료", decoded.keyword)
        assertEquals(publicId, decoded.publicId)
    }

    @Test
    fun `base64가 아닌 문자열은 거절한다`() {
        assertInvalidCursor { ProductKeywordSearchCursorCodec.decode("%%%not-base64%%%") }
    }

    @Test
    fun `잘못된 태그는 거절한다`() {
        assertInvalidCursor { ProductKeywordSearchCursorCodec.decode(crafted("x:${token("사료")}:$publicId")) }
    }

    @Test
    fun `파트 수가 다른 페이로드는 거절한다`() {
        assertInvalidCursor { ProductKeywordSearchCursorCodec.decode(crafted("l:${token("사료")}")) } // publicId 없음
        assertInvalidCursor { ProductKeywordSearchCursorCodec.decode(crafted("l:${token("사료")}:$publicId:extra")) }
    }

    @Test
    fun `base64url이 아닌 검색어 토큰은 거절한다`() {
        assertInvalidCursor { ProductKeywordSearchCursorCodec.decode(crafted("l:@@@:$publicId")) }
    }

    @Test
    fun `빈 publicId는 거절한다`() {
        assertInvalidCursor { ProductKeywordSearchCursorCodec.decode(crafted("l:${token("사료")}:")) }
    }
}
