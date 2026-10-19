package com.aechak.domain.payment

import com.aechak.common.error.BusinessException
import com.aechak.domain.payment.enums.PaymentMethod
import com.aechak.domain.payment.enums.PaymentStatus
import com.aechak.domain.payment.error.PaymentErrorCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
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

    @Test
    fun `취소 요청은 상태만 바꾸고 금액 원장은 건드리지 않는다`() {
        val requested = approved().requestCancel(5_000L)

        assertEquals(PaymentStatus.CANCEL_REQUESTED, requested.status)
        assertEquals(13_000L, requested.cancellableAmount, "PG 응답 전에 취소가능액을 깎으면 대사가 금액 불일치 오보를 낸다")
        assertEquals(0L, requested.cancelledAmount, "확정 전에는 누적 취소액도 늘지 않는다")
        assertNotNull(requested.cancelRequestedAt, "요청 시각이 없으면 응답을 못 받은 건을 찾을 수 없다")
    }

    @Test
    fun `전액 취소가 확정되면 취소가능액이 0이 되고 원승인액은 그대로다`() {
        val cancelled = approved().requestCancel(13_000L).completeCancel(13_000L)

        assertEquals(PaymentStatus.CANCELLED, cancelled.status)
        assertEquals(0L, cancelled.cancellableAmount)
        assertEquals(13_000L, cancelled.cancelledAmount)
        assertEquals(13_000L, cancelled.targetAmount, "원승인액을 깎으면 모든 취소 건이 대사에서 금액 불일치로 떨어진다")
        assertEquals(13_000L, cancelled.realPaidAmount, "실결제액도 취소로 줄지 않는다")
    }

    @Test
    fun `부분 취소가 확정되면 남은 취소가능액만 줄고 부분취소 상태가 된다`() {
        val partial = approved().requestCancel(5_000L).completeCancel(5_000L)

        assertEquals(PaymentStatus.PARTIAL_CANCELLED, partial.status)
        assertEquals(8_000L, partial.cancellableAmount)
        assertEquals(5_000L, partial.cancelledAmount)
    }

    @Test
    fun `부분 취소를 두 번 받아 잔액이 0이 되면 전액 취소로 종결된다`() {
        val closed =
            approved()
                .requestCancel(5_000L)
                .completeCancel(5_000L)
                .requestCancel(8_000L)
                .completeCancel(8_000L)

        assertEquals(PaymentStatus.CANCELLED, closed.status)
        assertEquals(0L, closed.cancellableAmount)
        assertEquals(13_000L, closed.cancelledAmount, "누적 취소액은 두 번의 합이어야 한다")
    }

    @Test
    fun `취소가능액을 넘는 요청은 60015로 차단된다`() {
        val partial = approved().requestCancel(5_000L).completeCancel(5_000L)

        val e = assertFailsWith<BusinessException> { partial.requestCancel(8_001L) }

        assertEquals(PaymentErrorCode.CANCEL_AMOUNT_EXCEEDS_CANCELLABLE, e.errorCode, "남은 잔액을 넘기면 PG가 아니라 우리가 먼저 막아야 한다")
    }

    @Test
    fun `PG가 돌려준 확정액이 취소가능액을 넘으면 60015로 차단된다`() {
        val requested = approved().requestCancel(5_000L)

        val e = assertFailsWith<BusinessException> { requested.completeCancel(13_001L) }

        assertEquals(PaymentErrorCode.CANCEL_AMOUNT_EXCEEDS_CANCELLABLE, e.errorCode, "원장을 음수로 만드는 반영은 받지 않는다")
    }

    @Test
    fun `0원 이하 취소는 60007로 차단된다`() {
        val e = assertFailsWith<BusinessException> { approved().requestCancel(0L) }

        assertEquals(PaymentErrorCode.INVALID_PAYMENT_AMOUNT, e.errorCode)
    }

    @Test
    fun `취소 진행 중인 결제의 추가 취소 요청은 60016으로 차단된다`() {
        val requested = approved().requestCancel(5_000L)

        val e = assertFailsWith<BusinessException> { requested.requestCancel(1_000L) }

        assertEquals(PaymentErrorCode.PAYMENT_CANCEL_IN_PROGRESS, e.errorCode, "선점에 진 쪽이 받아야 하는 코드다")
    }

    @Test
    fun `미승인 결제와 전액 취소된 결제의 취소 요청은 60014로 차단된다`() {
        val pending = assertFailsWith<BusinessException> { payment().requestCancel(1_000L) }
        val closed =
            assertFailsWith<BusinessException> {
                approved().requestCancel(13_000L).completeCancel(13_000L).requestCancel(1_000L)
            }

        assertEquals(PaymentErrorCode.PAYMENT_NOT_CANCELLABLE, pending.errorCode, "결제도 안 된 건을 PG에 취소 요청하면 안 된다")
        assertEquals(PaymentErrorCode.PAYMENT_NOT_CANCELLABLE, closed.errorCode, "남은 취소가능액이 없으면 요청 자체가 성립하지 않는다")
    }

    @Test
    fun `선점하지 않은 결제의 취소 확정은 60014로 차단된다`() {
        val e = assertFailsWith<BusinessException> { approved().completeCancel(1_000L) }

        assertEquals(PaymentErrorCode.PAYMENT_NOT_CANCELLABLE, e.errorCode, "같은 확정이 두 번 반영되면 원장이 두 번 깎인다")
    }

    @Test
    fun `취소 실패는 누적 취소액에 따라 직전 상태로 되돌린다`() {
        val fromApproved = approved().requestCancel(5_000L).failCancel()
        val fromPartial =
            approved()
                .requestCancel(5_000L)
                .completeCancel(5_000L)
                .requestCancel(3_000L)
                .failCancel()

        assertEquals(PaymentStatus.APPROVED, fromApproved.status, "취소된 적 없는 결제는 승인으로 돌아가야 재시도가 가능하다")
        assertEquals(13_000L, fromApproved.cancellableAmount, "선점 때 깎지 않았으므로 해제에서도 복원할 것이 없다")
        assertEquals(PaymentStatus.PARTIAL_CANCELLED, fromPartial.status)
        assertEquals(8_000L, fromPartial.cancellableAmount, "이미 확정된 부분취소는 실패 해제가 되돌리지 않는다")
    }

    @Test
    fun `선점 중이 아닌 결제의 취소 해제는 상태를 바꾸지 않는다`() {
        assertEquals(PaymentStatus.APPROVED, approved().failCancel().status, "실패 처리 경로가 원래 예외를 가리지 않게 그대로 둔다")
    }

    @Test
    fun `취소가 시작된 결제의 뒤늦은 승인은 60011로 차단된다`() {
        val requested = approved().requestCancel(5_000L)

        val e = assertFailsWith<BusinessException> { requested.approve(pgTxId = "tx-2", realPaidAmount = 13_000L) }

        assertEquals(PaymentErrorCode.PAYMENT_STATE_NOT_SUPPORTED, e.errorCode, "승인을 받아들이면 취소가능액이 승인액으로 되돌아간다")
    }

    private fun approved() = payment().approve(pgTxId = "tx-1", realPaidAmount = 13_000L)
}
