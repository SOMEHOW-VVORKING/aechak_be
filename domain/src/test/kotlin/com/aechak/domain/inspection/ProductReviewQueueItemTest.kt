package com.aechak.domain.inspection

import com.aechak.domain.inspection.enums.ReviewQueueStatus
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * [단위] 관리자 검토 행의 전이를 고정함.
 *
 * 깨지면 닫힌 행을 다시 닫아 관리자 결정이 덮이거나, 사유 없는 반려가 셀러에게 빈 안내로 나감.
 */
class ProductReviewQueueItemTest {
    @Test
    fun `반려하면 사유가 남는다`() {
        val item = openItem()

        item.reject("효능 표현을 빼 주세요.")

        assertEquals(ReviewQueueStatus.REJECTED, item.status)
        assertEquals("효능 표현을 빼 주세요.", item.rejectReason, "셀러가 볼 사유가 남아야 한다")
    }

    @Test
    fun `빈 사유로는 반려할 수 없다`() {
        assertFailsWith<IllegalArgumentException>("빈 사유가 나가면 셀러가 무엇을 고칠지 모른다") {
            openItem().reject(" ")
        }
    }

    @Test
    fun `닫힌 행은 다시 닫을 수 없다`() {
        val item = openItem()
        item.approve()

        assertFailsWith<IllegalStateException>("승인된 행을 반려로 덮으면 관리자 결정이 뒤집힌다") { item.reject("사유") }
        assertFailsWith<IllegalStateException> { item.supersede() }
    }

    private fun openItem(): ProductReviewQueueItem = ProductReviewQueueItem.openForProduct(1L, 2L, "연어 건사료 2kg", LocalDateTime.now())
}
