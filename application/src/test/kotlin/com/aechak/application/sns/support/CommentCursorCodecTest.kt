package com.aechak.application.sns.support

import com.aechak.common.error.BusinessException
import com.aechak.common.error.CommonErrorCode
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** 계약: 댓글 목록 커서가 왕복되고 다른 게시물이나 다른 목록의 커서를 90002로 거절하는지 */
class CommentCursorCodecTest {
    private val post = "01JF9W3Q6R8T2V4X7Z9B1C3D5J"

    @Test
    fun `커서를 왕복하면 마지막 최상위 댓글 id가 보존된다`() {
        assertEquals(42L, CommentCursorCodec.decode(CommentCursorCodec.encode(post, 42L), post))
    }

    @Test
    fun `다른 게시물의 커서는 해시가 달라 거절한다`() {
        val cursor = CommentCursorCodec.encode(post, 1L)

        assertCursorRejected { CommentCursorCodec.decode(cursor, "01JF9W3Q6R8T2V4X7Z9B1C3D5K") }
    }

    @Test
    fun `다른 태그의 커서는 거절한다`() {
        val swapped = encodeRaw(decodeRaw(CommentCursorCodec.encode(post, 1L)).replaceFirst("cm:", "bk:"))

        assertCursorRejected { CommentCursorCodec.decode(swapped, post) }
    }

    @Test
    fun `깨진 커서와 형식이 맞지 않는 커서는 거절한다`() {
        val hash = decodeRaw(CommentCursorCodec.encode(post, 1L)).substringAfterLast(':')

        assertCursorRejected { CommentCursorCodec.decode("%%%broken%%%", post) }
        assertCursorRejected { CommentCursorCodec.decode(encodeRaw("cm:1"), post) }
        assertCursorRejected { CommentCursorCodec.decode(encodeRaw("cm:x:$hash"), post) }
        assertCursorRejected { CommentCursorCodec.decode(encodeRaw("cm:0:$hash"), post) }
    }

    private fun encodeRaw(payload: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toByteArray(Charsets.UTF_8))

    private fun decodeRaw(cursor: String): String = String(Base64.getUrlDecoder().decode(cursor), Charsets.UTF_8)

    private fun assertCursorRejected(block: () -> Unit) {
        assertEquals(CommonErrorCode.INVALID_CURSOR, assertFailsWith<BusinessException>(block = block).errorCode)
    }
}
