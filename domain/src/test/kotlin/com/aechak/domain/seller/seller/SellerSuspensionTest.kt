package com.aechak.domain.seller.seller

import com.aechak.common.error.BusinessException
import com.aechak.domain.seller.error.SellerErrorCode
import com.aechak.domain.seller.sanction.SellerSanction
import com.aechak.domain.seller.sanction.enums.SellerSanctionReasonCode
import com.aechak.domain.seller.seller.enums.SellerStatus
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
    fun `입구가 받아 주는 가장 긴 상세 사유로 조립해도 사유 칸을 넘지 않는다`() {
        // 호출부는 어휘 라벨에 상세 사유를 이어 붙인 문구를 넘긴다. 상세가 입구 상한을 채우면
        // 라벨과 구분자만큼 컬럼을 넘어, 자르지 않으면 MySQL 이 Data too long 으로 정지 자체를
        // 되돌린다. 라벨이 길어지거나 구분자가 바뀌어도 걸리도록 실제 조합으로 만든다.
        val detail = "가".repeat(SellerSanction.REASON_DETAIL_MAX_LENGTH)
        SellerSanctionReasonCode.entries.forEach { code ->
            val composed = code.describe(detail)
            val seller = seller()

            seller.suspend(composed, null)

            assertTrue(
                seller.suspensionReason!!.length <= Seller.SUSPENSION_REASON_MAX_LENGTH,
                "$code 사유가 ${seller.suspensionReason!!.length}자로 컬럼 상한을 넘었다",
            )
            assertTrue(seller.suspensionReason!!.startsWith(code.label), "$code 라벨이 앞에 남아야 한다")
        }
    }

    @Test
    fun `상한 안에 드는 사유는 그대로 저장한다`() {
        val seller = seller()
        val composed = SellerSanctionReasonCode.REPEATED_REPORT.describe("신고 5건 누적")

        seller.suspend(composed, null)

        assertEquals(composed, seller.suspensionReason)
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
