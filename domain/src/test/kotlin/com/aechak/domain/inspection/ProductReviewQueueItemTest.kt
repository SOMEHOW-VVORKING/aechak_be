package com.aechak.domain.inspection

import com.aechak.domain.inspection.enums.ReviewQueueStatus
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [단위] 관리자 검토 행의 전이를 고정함.
 *
 * 깨지면 닫힌 행을 다시 닫아 관리자 결정이 덮이거나, 사유 없는 반려가 셀러에게 빈 안내로 나감.
 * 이의 쪽이 깨지면 셀러가 적은 사유가 옛 내용을 가리킨 채 남거나, 접수가 관리자 큐의 정렬을 밀음.
 */
class ProductReviewQueueItemTest {
    private val now: LocalDateTime = LocalDateTime.now()

    @Test
    fun `반려하면 사유가 남는다`() {
        val item = openItem()

        item.reject("효능 표현을 빼 주세요.", now)

        assertEquals(ReviewQueueStatus.REJECTED, item.status)
        assertEquals("효능 표현을 빼 주세요.", item.rejectReason, "셀러가 볼 사유가 남아야 한다")
        assertEquals(now, item.resolvedAt, "쿨다운의 기준이 닫힌 시각이다")
    }

    @Test
    fun `빈 사유로는 반려할 수 없다`() {
        assertFailsWith<IllegalArgumentException>("빈 사유가 나가면 셀러가 무엇을 고칠지 모른다") {
            openItem().reject(" ", now)
        }
    }

    @Test
    fun `닫힌 행은 다시 닫을 수 없다`() {
        val item = openItem()
        item.approve(now)

        assertFailsWith<IllegalStateException>("승인된 행을 반려로 덮으면 관리자 결정이 뒤집힌다") { item.reject("사유", now) }
        assertFailsWith<IllegalStateException> { item.supersede(now) }
    }

    @Test
    fun `이의를 접수해도 관리자 목록의 정렬 키는 그대로다`() {
        val blindedAt = now.minusDays(2)
        val item = ProductReviewQueueItem.openForProduct(1L, 2L, "연어 건사료 2kg", blindedAt)

        item.appeal("성분 설명입니다.", now, OLD_CONTENT)

        assertEquals(now, item.appealedAt)
        assertEquals("성분 설명입니다.", item.appealReason)
        // 접수가 정렬 키를 밀면 이의 폭주가 곧 큐 정렬 조작이 된다
        assertEquals(blindedAt, item.blindedAt)
        assertEquals(ReviewQueueStatus.PENDING_REVIEW, item.status, "접수는 행을 닫지 않는다")
    }

    @Test
    fun `이미 이의가 걸린 행에는 다시 걸 수 없다`() {
        val item = openItem()
        item.appeal("첫 사유입니다.", now, OLD_CONTENT)

        assertFailsWith<IllegalStateException>("두 번째 사유가 첫 사유를 덮으면 관리자가 무엇에 답하는지 알 수 없다") {
            item.appeal("둘째 사유입니다.", now, OLD_CONTENT)
        }
    }

    @Test
    fun `내용이 바뀐 판정으로 갱신하면 이의가 비워지고 흔적이 남는다`() {
        val item = openItem()
        item.appeal("성분 설명입니다.", now, OLD_CONTENT)

        item.refresh("연어 건사료 2kg", now.plusHours(1), NEW_CONTENT)

        // 고친 뒤의 사유는 지금 걸린 것을 가리키지 않는다. 비워서 재접수를 연다
        assertNull(item.appealedAt)
        assertNull(item.appealReason)
        // 비웠다는 흔적이 없으면 셀러 응답이 "접수한 적 없음"과 "무효가 됨"을 같은 모양으로 낸다
        assertTrue(item.appealCleared())
    }

    @Test
    fun `같은 내용에 판정이 또 와도 이의는 남는다`() {
        val item = openItem()
        item.appeal("성분 설명입니다.", now, OLD_CONTENT)

        item.refresh("연어 건사료 2kg", now.plusHours(1), OLD_CONTENT)

        // 재검수 기한 만료나 반려 상품의 재판정은 셀러가 한 일이 없다. 거기서 지우면
        // 모델 장애가 곧 "제출한 이의를 아무도 못 보는 상태"가 된다
        assertEquals(now, item.appealedAt)
        assertEquals("성분 설명입니다.", item.appealReason)
        assertFalse(item.appealCleared())
    }

    @Test
    fun `지문을 모르는 이의는 갱신으로 비워진다`() {
        val item = openItem()
        item.appeal("성분 설명입니다.", now, null)

        item.refresh("연어 건사료 2kg", now.plusHours(1), NEW_CONTENT)

        // 무엇에 대한 이의인지 모르면 지금 걸린 것을 가리킨다고 볼 근거가 없다
        assertNull(item.appealedAt)
        // 지문이 없어도 접수했다는 사실은 남는다. 흔적을 지문이 겸하면 이 경우에 함께 사라져
        // 셀러 응답이 "접수한 적 없음"과 같은 모양이 된다
        assertTrue(item.appealCleared())
    }

    @Test
    fun `무효화는 접수 횟수를 지우지 않는다`() {
        val item = openItem()
        item.appeal("성분 설명입니다.", now, OLD_CONTENT)
        item.refresh("연어 건사료 2kg", now.plusHours(1), NEW_CONTENT)

        item.appeal("고친 문구도 성분 설명입니다.", now.plusHours(2), NEW_CONTENT)

        // 무효화가 횟수까지 비우면 "한 글자 고쳐 새 판정을 받고 다시 접수"가 무한히 돈다
        assertEquals(2, item.appealAttemptCount)
    }

    private fun openItem(): ProductReviewQueueItem = ProductReviewQueueItem.openForProduct(1L, 2L, "연어 건사료 2kg", now)

    private companion object {
        const val OLD_CONTENT = "a"
        const val NEW_CONTENT = "b"
    }
}
