package com.aechak.application.sns.support

import com.aechak.application.sns.port.SnsRelationKind
import com.aechak.common.error.BusinessException
import com.aechak.common.error.CommonErrorCode
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

/**
 * 계약: 차단, 뮤트 관리 목록 커서가 왕복되고 다른 종류나 다른 주인의 커서를 90002로 거절하는지.
 * 깨지면 남의 목록 커서로 페이지를 넘기거나 커서에 사용자 식별자가 드러난다.
 */
class RelationCursorCodecTest {
    private val owner = "01JD5G7HJK8M2N4PQ6RSTV9WXY"

    @Test
    fun `차단과 뮤트 커서를 왕복하면 마지막 관계 행 id가 보존된다`() {
        val block = RelationCursorCodec.encode(SnsRelationKind.BLOCK, owner, 42L)
        val mute = RelationCursorCodec.encode(SnsRelationKind.MUTE, owner, 7L)

        assertEquals(42L, RelationCursorCodec.decode(block, SnsRelationKind.BLOCK, owner), "차단 커서를 왕복하면 마지막 관계 행 id가 보존되어야 한다")
        assertEquals(7L, RelationCursorCodec.decode(mute, SnsRelationKind.MUTE, owner), "뮤트 커서를 왕복하면 마지막 관계 행 id가 보존되어야 한다")
    }

    @Test
    fun `다른 종류 목록의 커서는 거절한다`() {
        val block = RelationCursorCodec.encode(SnsRelationKind.BLOCK, owner, 1L)

        assertCursorRejected { RelationCursorCodec.decode(block, SnsRelationKind.MUTE, owner) }
    }

    @Test
    fun `다른 주인의 커서는 해시가 달라 거절한다`() {
        val block = RelationCursorCodec.encode(SnsRelationKind.BLOCK, owner, 1L)

        assertCursorRejected { RelationCursorCodec.decode(block, SnsRelationKind.BLOCK, "01JE3F6GH9JK2M5NP8QRS4TVWX") }
    }

    @Test
    fun `태그만 바꾼 커서는 해시가 달라 거절한다`() {
        val block = RelationCursorCodec.encode(SnsRelationKind.BLOCK, owner, 1L)
        val swapped = encodeRaw(decodeRaw(block).replaceFirst("bk:", "mt:"))

        assertCursorRejected { RelationCursorCodec.decode(swapped, SnsRelationKind.MUTE, owner) }
    }

    @Test
    fun `깨진 커서와 형식이 맞지 않는 커서는 거절한다`() {
        val hash = decodeRaw(RelationCursorCodec.encode(SnsRelationKind.BLOCK, owner, 1L)).substringAfterLast(':')

        assertCursorRejected { RelationCursorCodec.decode("%%%broken%%%", SnsRelationKind.BLOCK, owner) }
        assertCursorRejected { RelationCursorCodec.decode(encodeRaw("bk:1"), SnsRelationKind.BLOCK, owner) }
        assertCursorRejected { RelationCursorCodec.decode(encodeRaw("bk:1:$hash:x"), SnsRelationKind.BLOCK, owner) }
        assertCursorRejected { RelationCursorCodec.decode(encodeRaw("bk:not-a-number:$hash"), SnsRelationKind.BLOCK, owner) }
        assertCursorRejected { RelationCursorCodec.decode(encodeRaw("bk:0:$hash"), SnsRelationKind.BLOCK, owner) }
    }

    @Test
    fun `디코딩한 페이로드는 태그, 관계 행 id, 해시뿐이고 사용자 식별자가 없다`() {
        val parts = decodeRaw(RelationCursorCodec.encode(SnsRelationKind.BLOCK, owner, 42L)).split(':')

        assertEquals(3, parts.size, "커서 페이로드는 태그, 관계 행 id, 해시 세 칸이어야 한다")
        assertEquals("bk", parts[0], "차단 커서의 태그는 bk여야 한다")
        assertEquals("42", parts[1], "두 번째 칸은 마지막 관계 행 id여야 한다")
        assertFalse(owner in parts[2], "해시 칸에 목록 주인 publicId가 드러나면 안 된다")
    }

    private fun encodeRaw(payload: String): String =
        Base64
            .getUrlEncoder()
            .withoutPadding()
            .encodeToString(payload.toByteArray(Charsets.UTF_8))

    private fun decodeRaw(cursor: String): String = String(Base64.getUrlDecoder().decode(cursor), Charsets.UTF_8)

    private fun assertCursorRejected(block: () -> Unit) {
        val exception = assertFailsWith<BusinessException>(block = block)
        assertEquals(CommonErrorCode.INVALID_CURSOR, exception.errorCode, "거절한 커서는 90002로 실패해야 한다")
    }
}
