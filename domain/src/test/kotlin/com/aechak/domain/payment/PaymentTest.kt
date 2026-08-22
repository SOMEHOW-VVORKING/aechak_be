package com.aechak.domain.payment

import com.aechak.common.error.BusinessException
import com.aechak.domain.payment.enums.PaymentMethod
import com.aechak.domain.payment.enums.PaymentStatus
import com.aechak.domain.payment.error.PaymentErrorCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** 계약 테스트. 깨지면 음수 금액이 PG에 등록되거나, 준비 직후 행이 승인된 것으로 읽혀 금액 대조 기준이 무너진 것임. */
class PaymentTest {
    private fun payment(targetAmount: Long = 13_000L) =
        Payment.prepare(
            orderGroupId = 1L,
            paymentId = "01K2ZQZQZQZQZQZQZQZQZQZQZQ",
            method = PaymentMethod.CARD,
            targetAmount = targetAmount,
        )

    @Test
    fun `준비된 결제는 PENDING이고 승인 관련 값이 비어 있다`() {
        val prepared = payment()

        assertEquals(PaymentStatus.PENDING, prepared.status, "결제창을 띄우기 전이므로 PENDING이어야 한다")
        assertNull(prepared.realPaidAmount, "승인 전에 실결제 금액이 차 있으면 대조 기준이 무너진다")
        assertNull(prepared.transactionId, "PG 거래는 아직 생기지 않았다")
    }

    @Test
    fun `결제 예정 금액 0원은 허용한다`() {
        assertEquals(0L, payment(targetAmount = 0L).targetAmount, "적립금 전액 결제가 0원이라 경계는 열어 둬야 한다")
    }

    @Test
    fun `음수 결제 예정 금액은 60007로 차단된다`() {
        val e = assertFailsWith<BusinessException> { payment(targetAmount = -1L) }

        assertEquals(
            PaymentErrorCode.INVALID_PAYMENT_AMOUNT,
            e.errorCode,
            "음수 금액이면 60007이어야 한다. 통과시키면 PG에 음수 금액이 등록된다",
        )
    }
}
