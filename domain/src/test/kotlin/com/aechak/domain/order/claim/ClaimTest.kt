package com.aechak.domain.order.claim

import com.aechak.domain.order.claim.enums.ClaimType
import com.aechak.domain.order.group.DeliveryAddressSnapshot
import com.aechak.domain.order.group.OrderGroup
import com.aechak.domain.order.order.Order
import com.aechak.domain.order.order.OrderItem
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * 계약 테스트. 깨지면 클레임의 셀러·주문 스냅샷이 실제 품목이 속한 주문과 갈린 것이다 —
 * 그 상태가 되면 셀러 클레임 목록과 클레임 상세가 서로 다른 주문을 가리킨다.
 */
class ClaimTest {
    @Test
    fun `클레임은 주문의 셀러와 주문 식별자를 스냅샷으로 들고 간다`() {
        val order = order(sellerId = 77L, id = 4242L)

        val claim = Claim.request(ClaimType.CANCEL, order, listOf(claimItem(order)))

        assertEquals(77L, claim.sellerId, "주문의 셀러가 스냅샷으로 들어가야 한다")
        assertEquals(4242L, claim.orderId, "주문 식별자가 스냅샷으로 들어가야 한다")
        assertEquals(1, claim.items.size)
    }

    @Test
    fun `대상 품목이 없으면 클레임을 만들 수 없다`() {
        assertFailsWith<IllegalArgumentException> {
            Claim.request(ClaimType.CANCEL, order(id = 4242L), emptyList())
        }
    }

    @Test
    fun `다른 주문의 품목으로는 클레임을 만들 수 없다`() {
        val target = order(sellerId = 77L, id = 4242L)
        val other = order(sellerId = 88L, id = 4343L)

        assertFailsWith<IllegalArgumentException> {
            Claim.request(ClaimType.CANCEL, target, listOf(claimItem(other)))
        }
    }

    @Test
    fun `저장되지 않은 주문으로는 클레임을 만들 수 없다`() {
        // 식별자가 0인 채로 스냅샷을 뜨면 주문이 저장된 뒤에도 0으로 남는다
        val unsaved = order(sellerId = 77L, id = 0L)

        assertFailsWith<IllegalArgumentException> {
            Claim.request(ClaimType.CANCEL, unsaved, listOf(claimItem(unsaved)))
        }
    }

    private fun claimItem(order: Order): ClaimItem = ClaimItem.of(order.items.first(), quantity = 1, refundAmount = 10_000L)

    /** 식별자는 저장 시점에 DB가 넣는다 — 스냅샷 계약을 보려면 저장된 주문이 필요해 여기서 직접 넣는다. */
    private fun order(
        sellerId: Long = 77L,
        id: Long = 4242L,
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
}
