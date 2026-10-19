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

    @Test
    fun `승인하면 거래 식별자와 실결제액이 기록되고 취소가능액이 초기화된다`() {
        val approved = payment().approve(pgTxId = "tx-1", realPaidAmount = 13_000L)

        assertEquals(PaymentStatus.APPROVED, approved.status)
        assertEquals("tx-1", approved.transactionId)
        assertEquals(13_000L, approved.realPaidAmount)
        assertEquals(13_000L, approved.cancellableAmount, "취소가능액은 실결제액으로 시작해야 부분취소 원장이 성립한다")
    }

    @Test
    fun `이미 승인된 결제의 재승인은 그대로 통과한다`() {
        val approved = payment().approve(pgTxId = "tx-1", realPaidAmount = 13_000L)

        val again = approved.approve(pgTxId = "tx-2", realPaidAmount = 13_000L)

        assertEquals("tx-1", again.transactionId, "콜백·웹훅이 겹쳐 도착해도 최초 승인 기록이 유지돼야 한다")
    }

    @Test
    fun `실패하면 PG 원본 실패 코드가 남고 재실패는 코드를 갱신한다`() {
        val failed = payment().fail("PG_LIMIT_EXCEEDED")

        assertEquals(PaymentStatus.FAILED, failed.status)
        assertEquals("PG_LIMIT_EXCEEDED", failed.failureCode)
        assertEquals("PG_DECLINED", failed.fail("PG_DECLINED").failureCode, "재시도 재실패는 마지막 시도의 기록으로 덮는다")
    }

    @Test
    fun `실패한 결제도 재시도 승인은 허용되고 실패 기록이 지워진다`() {
        val retried = payment().fail("PG_LIMIT_EXCEEDED").approve(pgTxId = "tx-2", realPaidAmount = 13_000L)

        assertEquals(PaymentStatus.APPROVED, retried.status, "실패는 최종 상태가 아니라 마지막 시도의 기록 — 웹훅 백스톱도 이 전이를 탄다")
        assertNull(retried.failureCode, "승인으로 종결됐는데 실패 코드가 남으면 원장이 거짓말을 한다")
    }

    @Test
    fun `승인된 결제의 실패 통보는 60006으로 차단된다`() {
        val approved = payment().approve(pgTxId = "tx-1", realPaidAmount = 13_000L)

        val e = assertFailsWith<BusinessException> { approved.fail("PG_DECLINED") }

        assertEquals(PaymentErrorCode.PAYMENT_ALREADY_PAID, e.errorCode, "승인 뒤 실패 통보는 정상 흐름에 없는 이상 사건이다")
    }
}
