package com.aechak.domain.order.claim

import com.aechak.common.error.BusinessException
import com.aechak.domain.order.claim.enums.ClaimStatus
import com.aechak.domain.order.claim.enums.ClaimType
import com.aechak.domain.order.claim.enums.RefundStatus
import com.aechak.domain.order.claim.enums.SellerApprovalStatus
import com.aechak.domain.order.error.OrderErrorCode
import com.aechak.domain.order.group.DeliveryAddressSnapshot
import com.aechak.domain.order.group.OrderGroup
import com.aechak.domain.order.order.Order
import com.aechak.domain.order.order.OrderItem
import com.aechak.domain.order.order.enums.OrderStatus
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 클레임 상태 전이 규칙. 깨지면 거부·철회된 클레임이 되살아나거나, 승인되지 않은 건에 환불이 걸린다.
 */
class ClaimTransitionTest {
    @Test
    fun `취소 승인은 즉시 승인 상태로 끝나고 환불은 미집행으로 남는다`() {
        val claim = requestedClaim()

        claim.approveBySeller(null)

        assertEquals(ClaimStatus.APPROVED, claim.status)
        assertEquals(SellerApprovalStatus.APPROVED, claim.sellerApprovalStatus)
        assertEquals(RefundStatus.NONE, claim.refundStatus, "승인만으로 돈이 나가면 안 된다")
        assertNotNull(claim.approvedAt)
        assertNull(claim.resolvedAt, "환불이 끝나기 전까지는 종결 시각이 없다")
    }

    @Test
    fun `이미 승인된 클레임을 다시 승인하면 거절한다`() {
        val claim = requestedClaim().apply { approveBySeller(null) }

        val exception = assertFailsWith<BusinessException> { claim.approveBySeller(null) }

        assertEquals(OrderErrorCode.CLAIM_ALREADY_RESOLVED, exception.errorCode)
    }

    @Test
    fun `거부된 클레임은 승인으로 되살아나지 않는다`() {
        val claim = requestedClaim().apply { rejectBySeller("상품 하자가 확인되지 않습니다") }

        assertEquals(ClaimStatus.REJECTED, claim.status)
        assertEquals("상품 하자가 확인되지 않습니다", claim.rejectionReason)
        assertNotNull(claim.resolvedAt)
        assertEquals(
            OrderErrorCode.CLAIM_ALREADY_RESOLVED,
            assertFailsWith<BusinessException> { claim.approveBySeller(null) }.errorCode,
        )
    }

    @Test
    fun `거부 사유가 공백이면 거절한다`() {
        val claim = requestedClaim()

        val exception = assertFailsWith<BusinessException> { claim.rejectBySeller("   ") }

        assertEquals(OrderErrorCode.CLAIM_REJECTION_REASON_REQUIRED, exception.errorCode)
        assertEquals(ClaimStatus.CLAIM_REQUESTED, claim.status, "거절된 요청이 상태를 바꾸면 안 된다")
    }

    @Test
    fun `철회는 셀러 응답 대기 중에만 된다`() {
        val claim = requestedClaim()

        claim.withdrawByBuyer()

        assertEquals(ClaimStatus.WITHDRAWN, claim.status)
        assertNotNull(claim.resolvedAt)
    }

    @Test
    fun `승인된 클레임은 철회할 수 없다`() {
        val claim = requestedClaim().apply { approveBySeller(null) }

        val exception = assertFailsWith<BusinessException> { claim.withdrawByBuyer() }

        assertEquals(OrderErrorCode.INVALID_CLAIM_STATUS_TRANSITION, exception.errorCode)
    }

    @Test
    fun `환불 집행 표시는 승인된 건에만 걸린다`() {
        val requested = requestedClaim()

        assertEquals(
            OrderErrorCode.INVALID_CLAIM_STATUS_TRANSITION,
            assertFailsWith<BusinessException> { requested.markRefundRequested() }.errorCode,
        )

        val approved = requestedClaim().apply { approveBySeller(null) }
        approved.markRefundRequested()
        assertEquals(RefundStatus.REQUESTED, approved.refundStatus)

        // 선점은 한 번만 — 재호출이 통과하면 환불이 두 번 나간다
        assertEquals(
            OrderErrorCode.INVALID_CLAIM_STATUS_TRANSITION,
            assertFailsWith<BusinessException> { approved.markRefundRequested() }.errorCode,
        )
    }

    @Test
    fun `환불 실패는 재집행 경로를 남긴다`() {
        val claim = requestedClaim().apply { approveBySeller(null) }
        claim.markRefundRequested()

        claim.markRefundFailed()

        assertEquals(ClaimStatus.REFUND_FAILED, claim.status)
        assertEquals(RefundStatus.REFUND_FAILED, claim.refundStatus)
        assertTrue(ClaimStatus.REFUND_FAILED.canTransitionTo(ClaimStatus.APPROVED))
    }

    @Test
    fun `환불 완료는 클레임을 종결한다`() {
        val claim = requestedClaim().apply { approveBySeller(null) }
        claim.markRefundRequested()

        claim.markRefunded()

        assertEquals(RefundStatus.DONE, claim.refundStatus)
        assertNotNull(claim.resolvedAt)
    }

    @Test
    fun `종결 상태에서는 어떤 전이도 열리지 않는다`() {
        listOf(ClaimStatus.REJECTED, ClaimStatus.WITHDRAWN, ClaimStatus.ADMIN_OVERRIDDEN).forEach { terminal ->
            ClaimStatus.entries.forEach { next ->
                assertFalse(terminal.canTransitionTo(next), "$terminal → $next 가 열려 있다")
            }
        }
    }

    @Test
    fun `거부와 철회만 대상 품목을 풀어준다`() {
        assertEquals(setOf(ClaimStatus.REJECTED, ClaimStatus.WITHDRAWN), ClaimStatus.RELEASED)
        assertTrue(ClaimStatus.APPROVED.holdsOrderItem(), "승인된 클레임은 품목을 계속 붙잡아야 한다")
        assertTrue(ClaimStatus.REFUND_FAILED.holdsOrderItem(), "환불 실패 건도 품목을 붙잡아야 한다")
    }

    private fun requestedClaim(): Claim {
        val order = order()
        return Claim.request(
            order = order,
            claimType = ClaimType.CANCEL,
            reasonCode = "CHANGE_OF_MIND",
            reasonDetail = null,
            items = listOf(ClaimItem.of(order.items.first(), quantity = 1, refundAmount = 10_000L)),
            autoApproveDueAt = LocalDateTime.of(2026, 10, 20, 14, 0, 0),
        )
    }

    private fun order(): Order =
        Order
            .create(
                orderGroup = orderGroup(),
                sellerId = 77L,
                sellerNameSnapshot = "멍멍상회",
                allocatedCouponDiscount = 0L,
                sellerShippingFee = 3_000L,
                baseShippingFeeSnapshot = 3_000L,
                freeShippingThresholdSnapshot = null,
                items =
                    listOf(
                        OrderItem.of(
                            productId = 1L,
                            optionCombinationId = 2L,
                            optionNameSnapshot = "기본 / 1개",
                            quantity = 1,
                            unitPriceSnapshot = 10_000L,
                            discountAllocatedAmount = 0L,
                            productVersionId = 3L,
                        ),
                    ),
            ).apply {
                Order::class.java
                    .getDeclaredField("id")
                    .apply { isAccessible = true }
                    .setLong(this, 4242L)
                Order::class.java
                    .getDeclaredField("status")
                    .apply { isAccessible = true }
                    .set(this, OrderStatus.PAID)
            }

    private fun orderGroup(): OrderGroup =
        OrderGroup.create(
            buyerId = 1L,
            deliveryAddressId = 100L,
            deliveryAddress =
                DeliveryAddressSnapshot(
                    receiverNameEnc = "enc-receiver",
                    contactNumberEnc = "enc-contact",
                    zipCode = "06236",
                    baseAddress = "서울특별시 강남구 테헤란로 1",
                    detailAddress = null,
                    deliveryMemo = null,
                ),
            usedPoint = 0L,
            totalProductAmount = 10_000L,
            totalShippingFee = 3_000L,
            idempotencyKey = "claim-transition-1",
            expiresAt = LocalDateTime.of(2026, 10, 18, 12, 0, 0),
        )
}
