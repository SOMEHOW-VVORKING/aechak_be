package com.aechak.application.sns.support

import com.aechak.application.sns.port.SnsPostTab
import com.aechak.common.error.BusinessException
import com.aechak.common.error.CommonErrorCode
import java.time.Instant
import java.time.LocalDateTime
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * 계약: 피드 커서가 기준 시각과 마지막 게시물 공개 ID를 마이크로초까지 왕복하고 다른 목록의 커서, 다른 주인의 프로필 탭 커서,
 * 깨진 커서를 90002로 거절하는지.
 * 깨지면 다음 페이지가 다른 기준 시각으로 잘리거나 다른 목록이나 다른 주인의 커서로 페이지를 넘긴다.
 */
class SnsFeedCursorCodecTest {
    private val lastPostPublicId = "01JG3A5C7E9G1J3K5M7P9R1T3V"

    @Test
    fun `탐색 커서를 왕복하면 기준 시각과 마지막 게시물 공개 ID가 보존된다`() {
        val anchor = LocalDateTime.of(2026, 11, 5, 9, 30, 15, 123_456_000)

        val decoded = SnsFeedCursorCodec.decodeExplore(SnsFeedCursorCodec.encodeExplore(anchor, lastPostPublicId))

        assertEquals(anchor, decoded.anchor, "기준 시각이 보존되어야 한다")
        assertEquals(lastPostPublicId, decoded.lastPostPublicId, "마지막 게시물 공개 ID가 보존되어야 한다")
    }

    @Test
    fun `시각은 UTC 기준 epoch 마이크로초로 담고 마이크로초 아래는 버린다`() {
        val anchor = LocalDateTime.of(2026, 11, 5, 9, 30, 15, 123_456_789)
        val expectedMicros = Instant.parse("2026-11-05T09:30:15.123456Z").let { it.epochSecond * 1_000_000 + it.nano / 1_000 }

        val cursor = SnsFeedCursorCodec.encodeExplore(anchor, lastPostPublicId)

        assertEquals("ex:$expectedMicros:$lastPostPublicId", decodeRaw(cursor), "시각은 UTC 기준 epoch 마이크로초여야 한다")
        assertEquals(
            LocalDateTime.of(2026, 11, 5, 9, 30, 15, 123_456_000),
            SnsFeedCursorCodec.decodeExplore(cursor).anchor,
            "마이크로초까지 보존하고 그 아래는 버려야 한다",
        )
    }

    @Test
    fun `다른 태그의 커서는 거절한다`() {
        assertCursorRejected { SnsFeedCursorCodec.decodeExplore(encodeRaw("fl:1:2:$lastPostPublicId")) }
        assertCursorRejected { SnsFeedCursorCodec.decodeExplore(encodeRaw("xx:1:$lastPostPublicId")) }
    }

    @Test
    fun `깨진 base64 커서는 거절한다`() {
        assertCursorRejected { SnsFeedCursorCodec.decodeExplore("%%%broken%%%") }
    }

    @Test
    fun `칸 수, 시각, 공개 ID가 형식에 맞지 않는 커서는 거절한다`() {
        assertCursorRejected { SnsFeedCursorCodec.decodeExplore(encodeRaw("ex:1")) }
        assertCursorRejected { SnsFeedCursorCodec.decodeExplore(encodeRaw("ex:1:$lastPostPublicId:x")) }
        assertCursorRejected { SnsFeedCursorCodec.decodeExplore(encodeRaw("ex:not-a-number:$lastPostPublicId")) }
        assertCursorRejected { SnsFeedCursorCodec.decodeExplore(encodeRaw("ex:0:$lastPostPublicId")) }
        assertCursorRejected { SnsFeedCursorCodec.decodeExplore(encodeRaw("ex:1:")) }
    }

    @Test
    fun `인기 커서를 왕복하면 순위를 정한 분과 다음에 읽을 자리가 보존된다`() {
        val cursor = SnsFeedCursorCodec.encodePopular(29_897_280L, 20)

        val decoded = SnsFeedCursorCodec.decodePopular(cursor)

        assertEquals("pp:29897280:20", decodeRaw(cursor), "인기 커서는 분과 자리만 담아야 한다")
        assertEquals(29_897_280L, decoded.epochMinute, "순위를 정한 분이 보존되어야 한다")
        assertEquals(20, decoded.offset, "다음에 읽을 자리가 보존되어야 한다")
    }

    @Test
    fun `인기 커서의 자리가 음수거나 1,000 이상이면 거절한다`() {
        assertCursorRejected { SnsFeedCursorCodec.decodePopular(encodeRaw("pp:29897280:-1")) }
        assertCursorRejected { SnsFeedCursorCodec.decodePopular(encodeRaw("pp:29897280:1000")) }
        assertEquals(999, SnsFeedCursorCodec.decodePopular(encodeRaw("pp:29897280:999")).offset, "999는 받아야 한다")
        assertEquals(0, SnsFeedCursorCodec.decodePopular(encodeRaw("pp:29897280:0")).offset, "0은 받아야 한다")
    }

    @Test
    fun `인기 커서의 분이 숫자가 아니거나 양수가 아니면 거절한다`() {
        assertCursorRejected { SnsFeedCursorCodec.decodePopular(encodeRaw("pp:minute:0")) }
        assertCursorRejected { SnsFeedCursorCodec.decodePopular(encodeRaw("pp:0:0")) }
        assertCursorRejected { SnsFeedCursorCodec.decodePopular(encodeRaw("pp:-5:0")) }
    }

    @Test
    fun `탐색 커서와 인기 커서는 서로의 목록에서 거절한다`() {
        val explore = SnsFeedCursorCodec.encodeExplore(LocalDateTime.of(2026, 11, 5, 9, 30), lastPostPublicId)

        assertCursorRejected { SnsFeedCursorCodec.decodePopular(explore) }
        assertCursorRejected { SnsFeedCursorCodec.decodeExplore(SnsFeedCursorCodec.encodePopular(29_897_280L, 0)) }
    }

    @Test
    fun `팔로잉 커서를 왕복하면 기준 시각과 마지막 시각이 마이크로초까지 보존된다`() {
        val anchor = LocalDateTime.of(2026, 11, 5, 9, 30, 15, 123_456_000)
        val lastTime = LocalDateTime.of(2026, 11, 4, 22, 1, 2, 654_321_000)

        val decoded = SnsFeedCursorCodec.decodeFollowing(SnsFeedCursorCodec.encodeFollowing(anchor, lastTime, lastPostPublicId))

        assertEquals(anchor, decoded.anchor, "기준 시각이 마이크로초까지 보존되어야 한다")
        assertEquals(lastTime, decoded.lastTime, "마지막 시각이 마이크로초까지 보존되어야 한다")
        assertEquals(lastPostPublicId, decoded.lastPostPublicId, "마지막 게시물 공개 ID가 보존되어야 한다")
    }

    @Test
    fun `팔로잉 커서는 다른 목록의 커서와 시각이 양수가 아닌 커서를 거절한다`() {
        val explore = SnsFeedCursorCodec.encodeExplore(LocalDateTime.of(2026, 11, 5, 9, 30), lastPostPublicId)

        assertCursorRejected { SnsFeedCursorCodec.decodeFollowing(explore) }
        assertCursorRejected { SnsFeedCursorCodec.decodeExplore(encodeRaw("fl:1:2:$lastPostPublicId")) }
        assertCursorRejected { SnsFeedCursorCodec.decodeFollowing(encodeRaw("fl:1:0:$lastPostPublicId")) }
        assertCursorRejected { SnsFeedCursorCodec.decodeFollowing(encodeRaw("fl:1:2")) }
    }

    @Test
    fun `그리드 커서를 왕복하면 마지막 게시물 공개 ID가 보존된다`() {
        val cursor = SnsFeedCursorCodec.encodeUserPosts(SnsPostTab.POSTS, OWNER_PUBLIC_ID, lastPostPublicId)

        assertEquals(
            lastPostPublicId,
            SnsFeedCursorCodec.decodeUserPosts(cursor, SnsPostTab.POSTS, OWNER_PUBLIC_ID),
            "마지막 게시물 공개 ID가 보존되어야 한다",
        )
        assertEquals("gp", decodeRaw(cursor).substringBefore(":"), "그리드 커서는 gp 태그여야 한다")
    }

    @Test
    fun `다른 주인의 그리드 커서와 다른 목록의 커서는 거절한다`() {
        val cursor = SnsFeedCursorCodec.encodeUserPosts(SnsPostTab.POSTS, OWNER_PUBLIC_ID, lastPostPublicId)
        val explore = SnsFeedCursorCodec.encodeExplore(LocalDateTime.of(2026, 11, 5, 9, 30), lastPostPublicId)

        assertCursorRejected { SnsFeedCursorCodec.decodeUserPosts(cursor, SnsPostTab.POSTS, OTHER_OWNER_PUBLIC_ID) }
        assertCursorRejected { SnsFeedCursorCodec.decodeUserPosts(explore, SnsPostTab.POSTS, OWNER_PUBLIC_ID) }
        assertCursorRejected { SnsFeedCursorCodec.decodeExplore(cursor) }
    }

    @Test
    fun `영상 탭 커서를 왕복하면 마지막 게시물 공개 ID가 보존되고 그리드 커서와 서로 거절한다`() {
        val videos = SnsFeedCursorCodec.encodeUserPosts(SnsPostTab.VIDEOS, OWNER_PUBLIC_ID, lastPostPublicId)
        val posts = SnsFeedCursorCodec.encodeUserPosts(SnsPostTab.POSTS, OWNER_PUBLIC_ID, lastPostPublicId)

        assertEquals(
            lastPostPublicId,
            SnsFeedCursorCodec.decodeUserPosts(videos, SnsPostTab.VIDEOS, OWNER_PUBLIC_ID),
            "마지막 게시물 공개 ID가 보존되어야 한다",
        )
        assertEquals("gv", decodeRaw(videos).substringBefore(":"), "영상 탭 커서는 gv 태그여야 한다")
        assertCursorRejected { SnsFeedCursorCodec.decodeUserPosts(posts, SnsPostTab.VIDEOS, OWNER_PUBLIC_ID) }
        assertCursorRejected { SnsFeedCursorCodec.decodeUserPosts(videos, SnsPostTab.POSTS, OWNER_PUBLIC_ID) }
    }

    @Test
    fun `리포스트 탭 커서를 왕복하면 마지막 리포스트 행 id가 보존된다`() {
        val cursor = SnsFeedCursorCodec.encodeUserReposts(OWNER_PUBLIC_ID, 318L)

        assertEquals(318L, SnsFeedCursorCodec.decodeUserReposts(cursor, OWNER_PUBLIC_ID), "마지막 리포스트 행 id가 보존되어야 한다")
        assertEquals("gr", decodeRaw(cursor).substringBefore(":"), "리포스트 탭 커서는 gr 태그여야 한다")
    }

    @Test
    fun `리포스트 탭은 다른 주인의 커서, 다른 탭의 커서, 양수가 아닌 행 id를 거절한다`() {
        val cursor = SnsFeedCursorCodec.encodeUserReposts(OWNER_PUBLIC_ID, 1L)
        val grid = SnsFeedCursorCodec.encodeUserPosts(SnsPostTab.POSTS, OWNER_PUBLIC_ID, lastPostPublicId)

        assertCursorRejected { SnsFeedCursorCodec.decodeUserReposts(cursor, OTHER_OWNER_PUBLIC_ID) }
        assertCursorRejected { SnsFeedCursorCodec.decodeUserReposts(grid, OWNER_PUBLIC_ID) }
        assertCursorRejected { SnsFeedCursorCodec.decodeUserPosts(cursor, SnsPostTab.POSTS, OWNER_PUBLIC_ID) }
        listOf("0", "-1", "abc").forEach { id ->
            val raw = decodeRaw(cursor).removeSuffix(":1") + ":$id"
            assertCursorRejected { SnsFeedCursorCodec.decodeUserReposts(encodeRaw(raw), OWNER_PUBLIC_ID) }
        }
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

    companion object {
        private const val OWNER_PUBLIC_ID = "01JG2K7M9P3R5T8V1X4Z6B8D0F"
        private const val OTHER_OWNER_PUBLIC_ID = "01JG4B6D8F0H2K4M6P8R0T2W4Y"
    }
}
