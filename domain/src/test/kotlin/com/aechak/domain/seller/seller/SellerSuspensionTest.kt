package com.aechak.domain.seller.seller

import com.aechak.common.error.BusinessException
import com.aechak.domain.seller.error.SellerErrorCode
import com.aechak.domain.seller.seller.enums.SellerStatus
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * 계약 — 허용 전이표를 엔티티가 막는다.
 * 셀러센터 쓰기와 노출 조회의 게이트가 전부 status 한 칼럼을 보고 있어, 이 전이가 틀리면
 * 정지가 걸리지 않거나 탈퇴한 셀러가 영업 상태로 되살아난다.
 */
class SellerSuspensionTest {
    private fun seller(): Seller = Seller.open(userId = 1L, storeName = "애착상회", baseShippingFee = 0L)

    @Test
    fun `정지는 직전 상태를 보관하고 사유와 해제 예정 시각을 적는다`() {
        val seller = seller()
        val until = LocalDateTime.now().plusDays(7)

        seller.suspend("신고 누적 — 3건", until)

        assertEquals(SellerStatus.SUSPENDED, seller.status)
        assertEquals(SellerStatus.ACTIVE, seller.previousStatus)
        assertEquals("신고 누적 — 3건", seller.suspensionReason)
        assertEquals(until, seller.suspensionUntil)
    }

    @Test
    fun `해제는 정지 직전 상태로 되돌리고 사유를 비운다`() {
        val seller = seller()
        seller.suspend("신고 누적", LocalDateTime.now().plusDays(3))

        seller.releaseSuspension()

        assertEquals(SellerStatus.ACTIVE, seller.status)
        assertNull(seller.previousStatus)
        assertNull(seller.suspensionReason)
        assertNull(seller.suspensionUntil)
    }

    @Test
    fun `이미 정지된 셀러의 재정지는 10007로 거부된다`() {
        val seller = seller()
        seller.suspend("신고 누적", null)

        val exception = assertFailsWith<BusinessException> { seller.suspend("다른 사유", null) }

        assertEquals(SellerErrorCode.SELLER_ALREADY_SUSPENDED, exception.errorCode)
        assertEquals(SellerStatus.SUSPENDED, seller.status)
        assertEquals("신고 누적", seller.suspensionReason)
    }

    @Test
    fun `정지가 아닌 셀러의 해제는 10008로 거부된다`() {
        val exception = assertFailsWith<BusinessException> { seller().releaseSuspension() }

        assertEquals(SellerErrorCode.SELLER_NOT_SUSPENDED, exception.errorCode)
    }

    @Test
    fun `메모는 앞뒤 공백을 떼고 저장하고 빈 값은 지운 것으로 본다`() {
        val seller = seller()

        seller.updateAdminMemo("  CS 1024 확인  ")
        assertEquals("CS 1024 확인", seller.adminMemo)

        seller.updateAdminMemo("   ")
        assertNull(seller.adminMemo)
    }

    @Test
    fun `길이를 넘는 메모는 10011로 거부된다`() {
        val exception =
            assertFailsWith<BusinessException> {
                seller().updateAdminMemo("가".repeat(Seller.ADMIN_MEMO_MAX_LENGTH + 1))
            }

        assertEquals(SellerErrorCode.SELLER_SANCTION_TEXT_TOO_LONG, exception.errorCode)
    }
}
