package com.aechak.domain.order.group

import com.aechak.common.error.BusinessException
import com.aechak.domain.order.error.OrderErrorCode
import com.aechak.domain.order.group.enums.OrderGroupStatus
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 계약 테스트. 깨지면 결제가 끝난 주문그룹이 미결제 취소로 되돌려지거나, 재실행이 예외로 바뀌거나, 만료 경계가 밀린 것임. */
class OrderGroupTest {
    private val expiry = LocalDateTime.of(2026, 8, 18, 12, 0, 0)

    private fun orderGroup(
        expiresAt: LocalDateTime = expiry,
        usedPoint: Long = 0L,
        totalProductAmount: Long = 10_000L,
        totalShippingFee: Long = 3_000L,
    ) = OrderGroup.create(
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
        usedPoint = usedPoint,
        totalProductAmount = totalProductAmount,
        totalShippingFee = totalShippingFee,
        idempotencyKey = "order-group-1",
        expiresAt = expiresAt,
    )

    @Test
    fun `최종 결제금액은 받지 않고 상품금액과 배송비에서 적립금을 빼서 계산한다`() {
        val group = orderGroup(usedPoint = 5_000L)

        assertEquals(8_000L, group.finalPaymentAmount, "10000 더하기 3000 빼기 5000이어야 한다")
    }

    @Test
    fun `적립금으로 전액을 덮으면 50110으로 차단된다`() {
        val e = assertFailsWith<BusinessException> { orderGroup(usedPoint = 13_000L) }

        assertEquals(
            OrderErrorCode.FULL_POINT_PAYMENT_NOT_ALLOWED,
            e.errorCode,
            "0원 그룹은 결제 준비가 거절해 영영 결제할 수 없다 — 생성부터 50110으로 막아야 한다",
        )
    }

    @Test
    fun `결제 금액이 1원만 남아도 생성은 허용된다`() {
        val group = orderGroup(usedPoint = 12_999L)

        assertEquals(1L, group.finalPaymentAmount, "전액 차단은 정확히 0원만 막아야 한다 — 1원부터는 결제 가능")
    }

    @Test
    fun `적립금 1000원 미만 사용은 50103으로 차단된다`() {
        val e = assertFailsWith<BusinessException> { orderGroup(usedPoint = 999L) }

        assertEquals(
            OrderErrorCode.POINT_BELOW_MINIMUM_USAGE,
            e.errorCode,
            "최소 사용액(1,000원) 미만이면 50103이어야 한다. 0원(미사용)은 이 검증을 타지 않는다",
        )
    }

    @Test
    fun `적립금은 정확히 1000원부터 사용할 수 있다`() {
        val group = orderGroup(usedPoint = 1_000L)

        assertEquals(12_000L, group.finalPaymentAmount, "경계값 1,000원은 허용돼야 한다")
    }

    @Test
    fun `적립금이 결제 가능액을 넘으면 50102로 차단된다`() {
        val e = assertFailsWith<BusinessException> { orderGroup(usedPoint = 13_001L) }

        assertEquals(
            OrderErrorCode.POINT_EXCEEDS_PAYABLE_AMOUNT,
            e.errorCode,
            "적립금 상한을 넘기면 50102여야 한다. 통과시키면 결제금액이 음수인 주문이 만들어진다",
        )
    }

    @Test
    fun `음수 금액은 50101로 차단된다`() {
        val e = assertFailsWith<BusinessException> { orderGroup(totalProductAmount = -1L) }

        assertEquals(OrderErrorCode.INVALID_ORDER_GROUP_AMOUNT, e.errorCode, "입력 금액이 음수면 50101이어야 한다")
    }

    @Test
    fun `미결제 주문그룹은 취소된다`() {
        val group = orderGroup()

        group.cancelUnpaid()

        assertEquals(OrderGroupStatus.CANCELLED, group.status, "미결제 종결은 CANCELLED로 가야 한다")
    }

    @Test
    fun `결제된 주문그룹의 미결제 취소는 50100으로 차단된다`() {
        val group = orderGroup()
        group.markPaid()

        val e = assertFailsWith<BusinessException> { group.cancelUnpaid() }

        assertEquals(
            OrderErrorCode.INVALID_ORDER_GROUP_STATUS_TRANSITION,
            e.errorCode,
            "결제 완료 뒤 도착한 만료 취소는 50100이어야 한다",
        )
        assertEquals(OrderGroupStatus.PAID, group.status, "차단된 전이가 상태를 바꾸면 안 된다")
    }

    @Test
    fun `이미 취소된 주문그룹의 취소 재호출은 그냥 통과한다`() {
        val group = orderGroup()
        group.cancelUnpaid()

        group.cancelUnpaid()

        assertEquals(OrderGroupStatus.CANCELLED, group.status, "만료 배치 재실행이 예외가 되면 호출부가 정상과 사고를 못 가른다")
    }

    @Test
    fun `이미 결제된 주문그룹의 결제 재호출은 그냥 통과한다`() {
        val group = orderGroup()
        group.markPaid()

        group.markPaid()

        assertEquals(OrderGroupStatus.PAID, group.status, "결제 웹훅 재전달이 예외가 되면 멱등 계약을 못 지킨다")
    }

    @Test
    fun `취소된 주문그룹에 결제가 도착하면 50100으로 막는다`() {
        val group = orderGroup()
        group.cancelUnpaid()

        val e = assertFailsWith<BusinessException> { group.markPaid() }

        assertEquals(
            OrderErrorCode.INVALID_ORDER_GROUP_STATUS_TRANSITION,
            e.errorCode,
            "돈은 받았는데 주문이 취소된 사건이라 재실행과 갈라져야 한다. 여기가 뭉개지면 환불이 필요한 건이 조용히 사라진다",
        )
        assertEquals(OrderGroupStatus.CANCELLED, group.status, "차단된 전이가 상태를 바꾸면 안 된다")
    }

    @Test
    fun `만료 기준 시각과 같은 순간은 아직 만료가 아니다`() {
        val group = orderGroup()

        assertFalse(group.isExpired(now = expiry), "경계를 닫으면 TTL이 기준 시각 직전까지로 짧아진다")
    }

    @Test
    fun `만료 기준 시각을 지나면 만료다`() {
        val group = orderGroup()

        assertTrue(group.isExpired(now = expiry.plusSeconds(1)), "기준 시각을 넘기면 sweeper 대상이어야 한다")
    }
}
