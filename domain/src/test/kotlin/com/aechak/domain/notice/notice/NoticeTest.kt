package com.aechak.domain.notice.notice

import com.aechak.common.error.BusinessException
import com.aechak.domain.notice.error.NoticeErrorCode
import com.aechak.domain.notice.notice.enums.NoticeAudience
import com.aechak.domain.notice.notice.enums.NoticeCategory
import com.aechak.domain.notice.notice.enums.NoticeStatus
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NoticeTest {
    @Nested
    @DisplayName("초안 생성")
    inner class DraftCreation {
        @Test
        fun `초안은 DRAFT로 시작하고 게시 시각이 비어 있다`() {
            val notice = draft()

            assertEquals(NoticeStatus.DRAFT, notice.status)
            assertNull(notice.publishedAt)
            assertNull(notice.lastModifiedByAdminId)
            assertEquals(ADMIN_ID, notice.createdByAdminId)
            assertEquals(26, notice.publicId.length)
        }

        @Test
        fun `제목이 공백뿐이면 거절한다`() {
            assertErrorCode(NoticeErrorCode.NOTICE_TITLE_REQUIRED) { draft(title = "   ") }
        }

        @Test
        fun `제목이 상한을 넘으면 거절한다`() {
            assertErrorCode(NoticeErrorCode.NOTICE_TITLE_TOO_LONG) {
                draft(title = "가".repeat(Notice.MAX_TITLE_LENGTH + 1))
            }
        }

        @Test
        fun `제목이 상한과 같으면 통과한다`() {
            val notice = draft(title = "가".repeat(Notice.MAX_TITLE_LENGTH))

            assertEquals(Notice.MAX_TITLE_LENGTH, notice.title.length)
        }

        @Test
        fun `내용이 공백뿐이면 거절한다`() {
            assertErrorCode(NoticeErrorCode.NOTICE_CONTENT_REQUIRED) { draft(content = "\n\t ") }
        }

        @Test
        fun `내용이 상한을 넘으면 거절한다`() {
            assertErrorCode(NoticeErrorCode.NOTICE_CONTENT_TOO_LONG) {
                draft(content = "나".repeat(Notice.MAX_CONTENT_LENGTH + 1))
            }
        }

        @Test
        fun `내부 메모가 상한을 넘으면 거절한다`() {
            assertErrorCode(NoticeErrorCode.NOTICE_MEMO_TOO_LONG) {
                draft(internalMemo = "다".repeat(Notice.MAX_MEMO_LENGTH + 1))
            }
        }

        @Test
        fun `내부 메모는 없어도 된다`() {
            assertNull(draft(internalMemo = null).internalMemo)
        }
    }

    @Nested
    @DisplayName("게시")
    inner class Publishing {
        @Test
        fun `초안을 게시하면 상태와 게시 시각과 수정자가 함께 남는다`() {
            val notice = draft()

            notice.publish(OTHER_ADMIN_ID, PUBLISHED_AT)

            assertEquals(NoticeStatus.PUBLISHED, notice.status)
            assertEquals(PUBLISHED_AT, notice.publishedAt)
            assertEquals(OTHER_ADMIN_ID, notice.lastModifiedByAdminId)
        }

        @Test
        fun `이미 게시된 공지는 다시 게시할 수 없다`() {
            val notice = draft().apply { publish(ADMIN_ID, PUBLISHED_AT) }

            assertErrorCode(NoticeErrorCode.NOTICE_ALREADY_PUBLISHED) { notice.publish(ADMIN_ID, PUBLISHED_AT) }
        }

        /** 거절된 전이가 게시 시각을 건드리지 않았는지까지 본다 — 예외만 확인하면 부작용이 숨는다. */
        @Test
        fun `중복 게시 거절은 기존 게시 시각을 바꾸지 않는다`() {
            val notice = draft().apply { publish(ADMIN_ID, PUBLISHED_AT) }

            runCatching { notice.publish(OTHER_ADMIN_ID, PUBLISHED_AT.plusDays(1)) }

            assertEquals(PUBLISHED_AT, notice.publishedAt)
            assertEquals(ADMIN_ID, notice.lastModifiedByAdminId)
        }

        @Test
        fun `내린 공지를 다시 게시하면 게시 시각이 갱신된다`() {
            val notice = draft().apply { publish(ADMIN_ID, PUBLISHED_AT) }
            notice.unpublish(ADMIN_ID)
            val republishedAt = PUBLISHED_AT.plusDays(3)

            notice.publish(ADMIN_ID, republishedAt)

            assertEquals(NoticeStatus.PUBLISHED, notice.status)
            assertEquals(republishedAt, notice.publishedAt)
        }
    }

    @Nested
    @DisplayName("내림")
    inner class Unpublishing {
        @Test
        fun `게시된 공지를 내리면 게시 기록은 남는다`() {
            val notice = draft().apply { publish(ADMIN_ID, PUBLISHED_AT) }

            notice.unpublish(OTHER_ADMIN_ID)

            assertEquals(NoticeStatus.UNPUBLISHED, notice.status)
            assertEquals(PUBLISHED_AT, notice.publishedAt)
            assertEquals(OTHER_ADMIN_ID, notice.lastModifiedByAdminId)
        }

        @Test
        fun `초안은 내릴 수 없다`() {
            val notice = draft()

            assertErrorCode(NoticeErrorCode.NOTICE_NOT_PUBLISHED) { notice.unpublish(ADMIN_ID) }
            assertEquals(NoticeStatus.DRAFT, notice.status)
        }

        @Test
        fun `이미 내린 공지는 다시 내릴 수 없다`() {
            val notice = draft().apply { publish(ADMIN_ID, PUBLISHED_AT) }
            notice.unpublish(ADMIN_ID)

            assertErrorCode(NoticeErrorCode.NOTICE_NOT_PUBLISHED) { notice.unpublish(OTHER_ADMIN_ID) }
            assertEquals(ADMIN_ID, notice.lastModifiedByAdminId)
        }
    }

    @Nested
    @DisplayName("수정")
    inner class Editing {
        @Test
        fun `게시 중 공지를 고쳐도 상태와 게시 시각은 그대로다`() {
            val notice = draft().apply { publish(ADMIN_ID, PUBLISHED_AT) }

            notice.edit(
                adminId = OTHER_ADMIN_ID,
                title = "고친 제목",
                content = "고친 내용",
                category = NoticeCategory.POLICY,
                audience = NoticeAudience.SELLER,
                pinned = true,
                internalMemo = "법무 확인 완료",
            )

            assertEquals(NoticeStatus.PUBLISHED, notice.status)
            assertEquals(PUBLISHED_AT, notice.publishedAt)
            assertEquals("고친 제목", notice.title)
            assertEquals(NoticeCategory.POLICY, notice.category)
            assertEquals(NoticeAudience.SELLER, notice.audience)
            assertTrue(notice.pinned)
            assertEquals(OTHER_ADMIN_ID, notice.lastModifiedByAdminId)
        }

        @Test
        fun `수정도 생성과 같은 검증을 받는다`() {
            val notice = draft()

            assertErrorCode(NoticeErrorCode.NOTICE_CONTENT_REQUIRED) {
                notice.edit(ADMIN_ID, "제목", " ", NoticeCategory.SERVICE, NoticeAudience.ALL, false, null)
            }
        }

        /** 검증에 걸린 수정이 앞 필드만 반영하고 멈추면 애그리거트가 반쪽 상태로 남는다. */
        @Test
        fun `검증에 걸린 수정은 어떤 필드도 바꾸지 않는다`() {
            val notice = draft(title = "원래 제목", pinned = false)

            runCatching {
                notice.edit(
                    ADMIN_ID,
                    "새 제목",
                    "나".repeat(Notice.MAX_CONTENT_LENGTH + 1),
                    NoticeCategory.EVENT,
                    NoticeAudience.BUYER,
                    pinned = true,
                    internalMemo = null,
                )
            }

            assertEquals("원래 제목", notice.title)
            assertEquals(NoticeCategory.SERVICE, notice.category)
            assertEquals(NoticeAudience.ALL, notice.audience)
            assertFalse(notice.pinned)
            assertNull(notice.lastModifiedByAdminId)
        }
    }

    @Nested
    @DisplayName("노출 판정")
    inner class Visibility {
        @Test
        fun `게시되지 않은 공지는 어느 독자에게도 보이지 않는다`() {
            val notice = draft(audience = NoticeAudience.ALL)

            assertFalse(notice.isVisibleTo(NoticeAudience.BUYER))
            assertFalse(notice.isVisibleTo(NoticeAudience.SELLER))
        }

        @Test
        fun `전체 대상 공지는 구매자와 셀러 둘 다 본다`() {
            val notice = draft(audience = NoticeAudience.ALL).apply { publish(ADMIN_ID, PUBLISHED_AT) }

            assertTrue(notice.isVisibleTo(NoticeAudience.BUYER))
            assertTrue(notice.isVisibleTo(NoticeAudience.SELLER))
        }

        @Test
        fun `셀러 대상 공지는 구매자에게 보이지 않는다`() {
            val notice = draft(audience = NoticeAudience.SELLER).apply { publish(ADMIN_ID, PUBLISHED_AT) }

            assertTrue(notice.isVisibleTo(NoticeAudience.SELLER))
            assertFalse(notice.isVisibleTo(NoticeAudience.BUYER))
        }

        @Test
        fun `내린 공지는 대상이 맞아도 보이지 않는다`() {
            val notice = draft(audience = NoticeAudience.SELLER).apply { publish(ADMIN_ID, PUBLISHED_AT) }
            notice.unpublish(ADMIN_ID)

            assertFalse(notice.isVisibleTo(NoticeAudience.SELLER))
        }

        @Test
        fun `독자가 볼 수 있는 대상 값은 자기 것과 전체뿐이다`() {
            assertEquals(setOf(NoticeAudience.ALL, NoticeAudience.BUYER), NoticeAudience.BUYER.visibleTo())
            assertEquals(setOf(NoticeAudience.ALL, NoticeAudience.SELLER), NoticeAudience.SELLER.visibleTo())
        }

        /** 예약 게시를 넣기 전까지 상태 어휘가 셋이라는 사실을 못 박는다 — 넷이 되면 배치가 함께 와야 한다. */
        @Test
        fun `게시 상태는 초안_게시_내림 셋뿐이다`() {
            assertEquals(
                listOf(NoticeStatus.DRAFT, NoticeStatus.PUBLISHED, NoticeStatus.UNPUBLISHED),
                NoticeStatus.entries,
            )
        }
    }

    private fun draft(
        title: String = "10월 28일 새벽 결제 시스템 점검 안내",
        content: String = "02:00부터 04:00까지 결제 시스템 점검이 있어요.",
        category: NoticeCategory = NoticeCategory.SERVICE,
        audience: NoticeAudience = NoticeAudience.ALL,
        pinned: Boolean = false,
        internalMemo: String? = "PG사 점검 일정과 맞춰 둠",
    ): Notice = Notice.draft(ADMIN_ID, title, content, category, audience, pinned, internalMemo)

    private fun assertErrorCode(
        expected: NoticeErrorCode,
        block: () -> Unit,
    ) {
        assertEquals(expected, assertThrows<BusinessException>(block).errorCode)
    }

    private companion object {
        const val ADMIN_ID = 7L
        const val OTHER_ADMIN_ID = 11L
        val PUBLISHED_AT: LocalDateTime = LocalDateTime.of(2026, 10, 27, 18, 0)
    }
}
