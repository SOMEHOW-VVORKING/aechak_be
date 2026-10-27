package com.aechak.domain.order.claim

import com.aechak.common.error.BusinessException
import com.aechak.common.error.CommonErrorCode
import com.aechak.domain.order.claim.enums.ClaimReasonCode
import com.aechak.domain.order.claim.enums.ClaimSettlementStatus
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * 계약 테스트. 깨지면 클레임의 셀러·주문 스냅샷이 실제 품목이 속한 주문과 갈린 것이다 —
 * 그 상태가 되면 셀러 클레임 목록과 클레임 상세가 서로 다른 주문을 가리킨다.
 */
class ClaimTest {
    @Test
    fun `클레임은 주문의 셀러와 주문 식별자를 스냅샷으로 들고 간다`() {
        val order = order(sellerId = 77L, id = 4242L)

        val claim = request(order)

        assertEquals(77L, claim.sellerId, "주문의 셀러가 스냅샷으로 들어가야 한다")
        assertEquals(4242L, claim.orderId, "주문 식별자가 스냅샷으로 들어가야 한다")
        assertEquals(1, claim.items.size)
    }

    @Test
    fun `요청 직후에는 셀러 응답 대기이고 환불은 미집행이다`() {
        val claim = request(order())

        assertEquals(ClaimStatus.CLAIM_REQUESTED, claim.status)
        assertEquals(SellerApprovalStatus.REQUESTED, claim.sellerApprovalStatus)
        assertEquals(RefundStatus.NONE, claim.refundStatus)
        assertEquals(ClaimSettlementStatus.PENDING, claim.settlementStatus)
        assertNotNull(claim.requestedAt)
        assertNotNull(claim.autoApproveDueAt)
        assertEquals(ClaimReasonCode.CHANGE_OF_MIND, claim.reasonCode)
    }

    @Test
    fun `대상 품목이 없으면 클레임을 만들 수 없다`() {
        assertFailsWith<IllegalArgumentException> {
            Claim.request(
                order = order(id = 4242L),
                claimType = ClaimType.CANCEL,
                reasonCode = ClaimReasonCode.CHANGE_OF_MIND,
                reasonDetail = null,
                items = emptyList(),
                autoApproveDueAt = DUE_AT,
            )
        }
    }

    @Test
    fun `다른 주문의 품목으로는 클레임을 만들 수 없다`() {
        val target = order(sellerId = 77L, id = 4242L)
        val other = order(sellerId = 88L, id = 4343L)

        assertFailsWith<IllegalArgumentException> {
            Claim.request(
                order = target,
                claimType = ClaimType.CANCEL,
                reasonCode = ClaimReasonCode.CHANGE_OF_MIND,
                reasonDetail = null,
                items = listOf(claimItem(other)),
                autoApproveDueAt = DUE_AT,
            )
        }
    }

    @Test
    fun `저장되지 않은 주문으로는 클레임을 만들 수 없다`() {
        // 식별자가 0인 채로 스냅샷을 뜨면 주문이 저장된 뒤에도 0으로 남는다
        val unsaved = order(sellerId = 77L, id = 0L)

        assertFailsWith<IllegalArgumentException> { request(unsaved) }
    }

    @Test
    fun `발송된 주문에는 취소를 요청할 수 없다`() {
        val shipping = order(status = OrderStatus.SHIPPING)

        val exception = assertFailsWith<BusinessException> { request(shipping) }

        assertEquals(OrderErrorCode.CLAIM_NOT_REQUESTABLE, exception.errorCode)
    }

    @Test
    fun `반품과 교환은 배송완료 주문에만 걸린다`() {
        val delivered = order(status = OrderStatus.DELIVERED)

        assertEquals(ClaimType.RETURN, request(delivered, ClaimType.RETURN).claimType)
        assertEquals(ClaimType.EXCHANGE, request(delivered, ClaimType.EXCHANGE).claimType)
    }

    @Test
    fun `배송 중 주문에는 반품을 요청할 수 없다`() {
        val shipping = order(status = OrderStatus.SHIPPING)

        val exception = assertFailsWith<BusinessException> { request(shipping, ClaimType.RETURN) }

        assertEquals(OrderErrorCode.CLAIM_NOT_REQUESTABLE, exception.errorCode)
    }

    @Test
    fun `배송완료 주문에는 취소를 요청할 수 없다`() {
        val delivered = order(status = OrderStatus.DELIVERED)

        val exception = assertFailsWith<BusinessException> { request(delivered, ClaimType.CANCEL) }

        assertEquals(OrderErrorCode.CLAIM_NOT_REQUESTABLE, exception.errorCode)
    }

    /**
     * 셀러는 반품비 부담 주체를 코드만 보고 가른다 — 변심은 구매자, 불량·오배송은 셀러다.
     * 유형이 받지 않는 코드가 저장되면 그 구분이 무너지므로 생성 자체를 막는다.
     */
    @Test
    fun `유형이 받지 않는 사유로는 클레임을 만들 수 없다`() {
        val paid = order()
        val delivered = order(status = OrderStatus.DELIVERED)

        val rejected =
            listOf(
                Triple(paid, ClaimType.CANCEL, ClaimReasonCode.DEFECTIVE),
                Triple(paid, ClaimType.CANCEL, ClaimReasonCode.WRONG_ITEM),
                Triple(paid, ClaimType.CANCEL, ClaimReasonCode.NOT_AS_DESCRIBED),
                Triple(delivered, ClaimType.RETURN, ClaimReasonCode.PRICE_MISMATCH),
                Triple(delivered, ClaimType.RETURN, ClaimReasonCode.DELIVERY_DELAY),
                Triple(delivered, ClaimType.RETURN, ClaimReasonCode.WRONG_OPTION),
                Triple(delivered, ClaimType.EXCHANGE, ClaimReasonCode.CHANGE_OF_MIND),
                Triple(delivered, ClaimType.EXCHANGE, ClaimReasonCode.PRICE_MISMATCH),
                Triple(delivered, ClaimType.EXCHANGE, ClaimReasonCode.NOT_AS_DESCRIBED),
            )

        rejected.forEach { (target, claimType, reasonCode) ->
            val exception = assertFailsWith<BusinessException> { request(target, claimType, reasonCode) }
            assertEquals(OrderErrorCode.CLAIM_REASON_NOT_SELECTABLE, exception.errorCode, "$claimType + $reasonCode")
        }
    }

    @Test
    fun `유형이 받는 사유는 모두 클레임이 된다`() {
        ClaimType.entries.forEach { claimType ->
            val target = order(status = if (claimType == ClaimType.CANCEL) OrderStatus.PAID else OrderStatus.DELIVERED)
            ClaimReasonCode.selectableFor(claimType).forEach { reasonCode ->
                val claim = request(target, claimType, reasonCode, reasonDetail = "상세를 적었어요")
                assertEquals(reasonCode, claim.reasonCode, "$claimType + $reasonCode")
            }
        }
    }

    /** 상세를 받지 않으면 선택지가 전부 기타로 몰려 사유를 고정한 의미가 사라진다. */
    @Test
    fun `기타 사유는 상세가 없으면 클레임을 만들 수 없다`() {
        val paid = order()

        listOf(null, "", "   ").forEach { detail ->
            val exception =
                assertFailsWith<BusinessException> {
                    request(paid, ClaimType.CANCEL, ClaimReasonCode.OTHER, reasonDetail = detail)
                }
            assertEquals(OrderErrorCode.CLAIM_REASON_DETAIL_REQUIRED, exception.errorCode, "detail=$detail")
        }

        val claim = request(paid, ClaimType.CANCEL, ClaimReasonCode.OTHER, reasonDetail = "다른 곳에서 더 싸게 샀어요")

        assertEquals(ClaimReasonCode.OTHER, claim.reasonCode)
        assertEquals("다른 곳에서 더 싸게 샀어요", claim.reasonDetail)
    }

    @Test
    fun `환불 실패는 진행 상태와 환불 상태를 함께 움직이고 재집행이 둘을 함께 되돌린다`() {
        // 셀러 클레임 목록의 상태 필터가 이 동행에 기댄다 — 한 축만 움직이면 "환불 실패" 판정이 어느 축으로 거느냐에 따라 갈린다.
        val claim = request(order())
        claim.approveBySeller(null)
        claim.markRefundRequested()

        claim.markRefundFailed()
        assertEquals(ClaimStatus.REFUND_FAILED, claim.status)
        assertEquals(RefundStatus.REFUND_FAILED, claim.refundStatus)

        claim.retryRefund()
        assertEquals(ClaimStatus.APPROVED, claim.status)
        assertEquals(RefundStatus.NONE, claim.refundStatus)
    }

    @Test
    fun `운영 메모는 앞뒤 공백을 떼고 저장되고 공백만 보내면 지워진다`() {
        val claim = request(order())

        claim.updateSellerMemo("  고객 요청으로 환불 처리  ")
        assertEquals("고객 요청으로 환불 처리", claim.sellerMemo)

        claim.updateSellerMemo("   ")
        assertNull(claim.sellerMemo)

        claim.updateSellerMemo("다시 남긴다")
        claim.updateSellerMemo(null)
        assertNull(claim.sellerMemo)
    }

    @Test
    fun `운영 메모가 상한을 넘으면 저장하지 않고 터진다`() {
        val claim = request(order())
        claim.updateSellerMemo("남겨둘 메모")

        val exception =
            assertFailsWith<BusinessException> { claim.updateSellerMemo("가".repeat(Claim.SELLER_MEMO_MAX_LENGTH + 1)) }

        assertEquals(CommonErrorCode.INVALID_REQUEST, exception.errorCode)
        assertEquals("남겨둘 메모", claim.sellerMemo, "실패한 쓰기가 기존 메모를 지우면 안 된다")
    }

    @Test
    fun `종결된 클레임에도 운영 메모를 남길 수 있다`() {
        val claim = request(order())
        claim.rejectBySeller("상품 하자가 확인되지 않습니다")

        claim.updateSellerMemo("거부 사유 고객 안내 완료")

        assertEquals("거부 사유 고객 안내 완료", claim.sellerMemo)
    }

    /** 유형이 받지 않는 사유는 생성 자체가 막히므로 기본값을 유형에 맞춰 고른다 — 교환에는 변심이 없다. */
    private fun request(
        order: Order,
        claimType: ClaimType = ClaimType.CANCEL,
        reasonCode: ClaimReasonCode =
            if (claimType == ClaimType.EXCHANGE) ClaimReasonCode.DEFECTIVE else ClaimReasonCode.CHANGE_OF_MIND,
        reasonDetail: String? = "사이즈를 잘못 골랐어요",
    ): Claim =
        Claim.request(
            order = order,
            claimType = claimType,
            reasonCode = reasonCode,
            reasonDetail = reasonDetail,
            items = listOf(claimItem(order)),
            autoApproveDueAt = DUE_AT,
        )

    private fun claimItem(order: Order): ClaimItem = ClaimItem.of(order.items.first(), quantity = 1, refundAmount = 10_000L)

    /** 식별자는 저장 시점에 DB가 넣는다 — 스냅샷 계약을 보려면 저장된 주문이 필요해 여기서 직접 넣는다. */
    private fun order(
        sellerId: Long = 77L,
        id: Long = 4242L,
        status: OrderStatus = OrderStatus.PAID,
    ): Order =
        Order
            .create(
                orderGroup = orderGroup(),
                sellerId = sellerId,
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
                    .setLong(this, id)
                Order::class.java
                    .getDeclaredField("status")
                    .apply { isAccessible = true }
                    .set(this, status)
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
            idempotencyKey = "claim-test-1",
            expiresAt = LocalDateTime.of(2026, 10, 18, 12, 0, 0),
        )

    private companion object {
        val DUE_AT: LocalDateTime = LocalDateTime.of(2026, 10, 20, 14, 0, 0)
    }
}
