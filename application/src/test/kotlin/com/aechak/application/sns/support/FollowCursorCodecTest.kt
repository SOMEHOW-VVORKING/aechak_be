package com.aechak.application.sns.support

import com.aechak.application.sns.port.SnsFollowDirection
import com.aechak.application.sns.usecase.query.SnsFollowListQuery
import com.aechak.common.error.BusinessException
import com.aechak.common.error.CommonErrorCode
import java.text.Normalizer
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

/**
 * 계약: 팔로워, 팔로잉 목록 커서가 왕복되고 주인, 방향, 정규화한 검색어 중 하나라도 다른 요청의 커서를 90002로 거절하는지.
 * 깨지면 남의 목록이나 다른 검색어의 커서로 페이지를 넘기거나 커서에 사용자 식별자가 드러난다.
 */
class FollowCursorCodecTest {
    private val owner = "01JD5G7HJK8M2N4PQ6RSTV9WXY"
    private val followers = SnsFollowDirection.FOLLOWERS
    private val followings = SnsFollowDirection.FOLLOWINGS

    @Test
    fun `팔로워와 팔로잉 커서를 왕복하면 마지막 팔로우 행 id가 보존된다`() {
        val follower = FollowCursorCodec.encode(followers, owner, null, 42L)
        val following = FollowCursorCodec.encode(followings, owner, "코코", 7L)

        assertEquals(42L, FollowCursorCodec.decode(follower, followers, owner, null), "팔로워 커서를 왕복하면 마지막 팔로우 행 id가 보존되어야 한다")
        assertEquals(7L, FollowCursorCodec.decode(following, followings, owner, "코코"), "팔로잉 커서를 왕복하면 마지막 팔로우 행 id가 보존되어야 한다")
    }

    @Test
    fun `다른 방향 목록의 커서는 거절한다`() {
        val following = FollowCursorCodec.encode(followings, owner, null, 1L)

        assertCursorRejected { FollowCursorCodec.decode(following, followers, owner, null) }
    }

    @Test
    fun `태그만 바꾼 커서는 해시가 달라 거절한다`() {
        val following = FollowCursorCodec.encode(followings, owner, null, 1L)
        val swapped = encodeRaw(decodeRaw(following).replaceFirst("fg:", "fw:"))

        assertCursorRejected { FollowCursorCodec.decode(swapped, followers, owner, null) }
    }

    @Test
    fun `주인이나 검색어가 다른 요청의 커서는 해시가 달라 거절한다`() {
        val cursor = FollowCursorCodec.encode(followers, owner, "코코", 1L)

        assertCursorRejected { FollowCursorCodec.decode(cursor, followers, "01JE3F6GH9JK2M5NP8QRS4TVWX", "코코") }
        assertCursorRejected { FollowCursorCodec.decode(cursor, followers, owner, "보리") }
        assertCursorRejected { FollowCursorCodec.decode(cursor, followers, owner, null) }
    }

    @Test
    fun `앞뒤 공백이나 NFD로만 다른 검색어는 같은 해시라 커서가 통과한다`() {
        val nfd = Normalizer.normalize("코코", Normalizer.Form.NFD)
        val cursor = FollowCursorCodec.encode(followers, owner, normalized("코코"), 9L)

        assertEquals(9L, FollowCursorCodec.decode(cursor, followers, owner, normalized("  코코 ")), "앞뒤 공백만 다른 검색어의 커서는 통과해야 한다")
        assertEquals(9L, FollowCursorCodec.decode(cursor, followers, owner, normalized(nfd)), "NFD로만 다른 검색어의 커서는 통과해야 한다")
    }

    @Test
    fun `공백만 있는 검색어는 생략과 같은 해시라 검색어 없이 받은 커서가 통과한다`() {
        val cursor = FollowCursorCodec.encode(followers, owner, null, 5L)

        assertEquals(5L, FollowCursorCodec.decode(cursor, followers, owner, normalized("   ")), "공백만 있는 검색어는 생략과 같아 커서가 통과해야 한다")
    }

    @Test
    fun `깨진 커서와 형식이 맞지 않는 커서는 거절한다`() {
        val hash = decodeRaw(FollowCursorCodec.encode(followers, owner, null, 1L)).substringAfterLast(':')

        assertCursorRejected { FollowCursorCodec.decode("%%%broken%%%", followers, owner, null) }
        assertCursorRejected { FollowCursorCodec.decode(encodeRaw("fw:1"), followers, owner, null) }
        assertCursorRejected { FollowCursorCodec.decode(encodeRaw("fw:1:$hash:x"), followers, owner, null) }
        assertCursorRejected { FollowCursorCodec.decode(encodeRaw("fw:not-a-number:$hash"), followers, owner, null) }
        assertCursorRejected { FollowCursorCodec.decode(encodeRaw("fw:0:$hash"), followers, owner, null) }
    }

    @Test
    fun `디코딩한 페이로드는 태그, 팔로우 행 id, 해시뿐이고 사용자 식별자가 없다`() {
        val parts = decodeRaw(FollowCursorCodec.encode(followers, owner, "코코", 42L)).split(':')

        assertEquals(3, parts.size, "커서 페이로드는 태그, 팔로우 행 id, 해시 세 칸이어야 한다")
        assertEquals("fw", parts[0], "팔로워 커서의 태그는 fw여야 한다")
        assertEquals("42", parts[1], "두 번째 칸은 마지막 팔로우 행 id여야 한다")
        assertFalse(owner in parts[2], "해시 칸에 목록 주인 publicId가 드러나면 안 된다")
    }

    private fun normalized(raw: String): String? =
        SnsFollowListQuery(viewerId = 1L, ownerPublicId = owner, direction = followers, cursor = null, rawNickname = raw).nickname

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
