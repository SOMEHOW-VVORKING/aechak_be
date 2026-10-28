package com.aechak.application.payment.reconciliation.service

import com.aechak.application.payment.port.GatewayPaymentRecord
import com.aechak.application.payment.port.PaymentGatewayStatus
import com.aechak.domain.order.group.DeliveryAddressSnapshot
import com.aechak.domain.order.group.OrderGroup
import com.aechak.domain.order.group.enums.OrderGroupStatus
import com.aechak.domain.payment.Payment
import com.aechak.domain.payment.enums.PaymentMethod
import com.aechak.domain.payment.enums.PaymentStatus
import com.aechak.domain.payment.reconciliation.MismatchType
import com.aechak.domain.payment.reconciliation.ReconciliationResolution
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 판정표 계약. 깨지면 돈이 걸린 건이 자동으로 처리되거나(사람 확인 누락), 놓친 승인이 영원히 확정되지 않는다.
 * 각 테스트 이름이 표의 한 행이다.
 */
class PaymentReconciliationClassifierTest {
    private val amount = 13_000L

    private fun group(
        status: OrderGroupStatus,
        finalAmount: Long = amount,
    ): OrderGroup {
        val group =
            OrderGroup.create(
                buyerId = 1L,
                deliveryAddressId = 1L,
                deliveryAddress = DeliveryAddressSnapshot("n", "c", "12345", "서울", null, null),
                usedPoint = 0L,
                totalProductAmount = finalAmount,
                totalShippingFee = 0L,
                idempotencyKey = "k",
                expiresAt = LocalDateTime.now().plusMinutes(10),
            )
        when (status) {
            OrderGroupStatus.PAID -> {
                group.markPaid()
            }

            OrderGroupStatus.CANCELLED -> {
                group.cancelUnpaid()
            }

            OrderGroupStatus.PARTIALLY_CANCELLED -> {
                group.markPaid()
                group.applyClaimCancellation(fullyCancelled = false)
            }

            OrderGroupStatus.PENDING_PAYMENT -> {
                Unit
            }
        }
        return group
    }

    private fun payment(
        status: PaymentStatus,
        target: Long = amount,
        paid: Long? = if (status == PaymentStatus.PENDING || status == PaymentStatus.FAILED) null else amount,
        cancellable: Long? = paid,
        cancelledAmount: Long = 0L,
    ): Payment =
        Payment.restore(
            id = 7L,
            orderGroupId = 3L,
            paymentId = "01K2ZQZQZQZQZQZQZQZQZQZQZQ",
            method = PaymentMethod.CARD,
            targetAmount = target,
            status = status,
            transactionId = if (status == PaymentStatus.PENDING) null else "pg-tx",
            realPaidAmount = paid,
            failureCode = null,
            cancellableAmount = cancellable,
            cancelledAmount = cancelledAmount,
            cancelRequestedAt = if (status == PaymentStatus.CANCEL_REQUESTED) LocalDateTime.of(2026, 10, 15, 11, 0) else null,
            version = 0,
        )

    private fun pg(
        status: PaymentGatewayStatus,
        paid: Long = amount,
        cancelled: Long = 0L,
    ) = GatewayPaymentRecord(
        paymentKey = "01K2ZQZQZQZQZQZQZQZQZQZQZQ",
        status = status,
        totalAmount = amount,
        paidAmount = paid,
        cancelledAmount = cancelled,
        pgTxId = "pg-tx",
        statusChangedAt = LocalDateTime.of(2026, 10, 15, 12, 0),
    )

    private fun assertVerdict(
        expectedType: MismatchType,
        expectedResolution: ReconciliationResolution,
        payment: Payment?,
        group: OrderGroup?,
        gateway: GatewayPaymentRecord?,
    ) {
        val verdict = PaymentReconciliationClassifier.classify(payment, group, gateway)
        assertEquals(expectedType, verdict.mismatchType)
        assertEquals(expectedResolution, verdict.resolution)
    }

    @Test
    fun `양쪽 승인이고 금액 셋이 같으면 일치다`() =
        assertVerdict(
            MismatchType.MATCHED,
            ReconciliationResolution.NOT_REQUIRED,
            payment(PaymentStatus.APPROVED),
            group(OrderGroupStatus.PAID),
            pg(PaymentGatewayStatus.PAID),
        )

    @Test
    fun `양쪽 승인인데 PG 실결제액이 다르면 금액 불일치로 사람 확인이다`() =
        assertVerdict(
            MismatchType.AMOUNT_MISMATCH,
            ReconciliationResolution.MANUAL_REQUIRED,
            payment(PaymentStatus.APPROVED),
            group(OrderGroupStatus.PAID),
            pg(PaymentGatewayStatus.PAID, paid = 12_000L),
        )

    @Test
    fun `양쪽 승인인데 주문금액이 등록금액과 다르면 금액 불일치다`() =
        assertVerdict(
            MismatchType.AMOUNT_MISMATCH,
            ReconciliationResolution.MANUAL_REQUIRED,
            payment(PaymentStatus.APPROVED),
            group(OrderGroupStatus.PAID, finalAmount = 14_000L),
            pg(PaymentGatewayStatus.PAID),
        )

    @Test
    fun `PG 승인인데 우리는 결제대기면 자동 보정 대상이다`() =
        assertVerdict(
            MismatchType.PG_PAID_OURS_UNCONFIRMED,
            ReconciliationResolution.AUTO_PENDING,
            payment(PaymentStatus.PENDING),
            group(OrderGroupStatus.PENDING_PAYMENT),
            pg(PaymentGatewayStatus.PAID),
        )

    @Test
    fun `PG 승인이고 우리 결제는 실패 기록인데 그룹이 결제대기면 재시도 승인을 놓친 것이라 자동 보정 대상이다`() =
        assertVerdict(
            MismatchType.PG_PAID_OURS_UNCONFIRMED,
            ReconciliationResolution.AUTO_PENDING,
            payment(PaymentStatus.FAILED),
            group(OrderGroupStatus.PENDING_PAYMENT),
            pg(PaymentGatewayStatus.PAID),
        )

    @Test
    fun `승인 기록은 남았는데 그룹 전이가 유실됐으면 자동 보정 대상이다`() =
        assertVerdict(
            MismatchType.PG_PAID_OURS_UNCONFIRMED,
            ReconciliationResolution.AUTO_PENDING,
            payment(PaymentStatus.APPROVED),
            group(OrderGroupStatus.PENDING_PAYMENT),
            pg(PaymentGatewayStatus.PAID),
        )

    @Test
    fun `PG 승인인데 그룹이 취소됐으면 돈만 나간 건이라 사람 확인이다`() =
        assertVerdict(
            MismatchType.PG_PAID_GROUP_CANCELLED,
            ReconciliationResolution.MANUAL_REQUIRED,
            payment(PaymentStatus.PENDING),
            group(OrderGroupStatus.CANCELLED),
            pg(PaymentGatewayStatus.PAID),
        )

    @Test
    fun `그룹 취소가 금액 불일치보다 먼저다`() =
        assertVerdict(
            MismatchType.PG_PAID_GROUP_CANCELLED,
            ReconciliationResolution.MANUAL_REQUIRED,
            payment(PaymentStatus.APPROVED),
            group(OrderGroupStatus.CANCELLED),
            pg(PaymentGatewayStatus.PAID, paid = 1L),
        )

    @Test
    fun `우리는 승인인데 PG는 미성사면 가장 위험한 건이라 사람 확인이다`() {
        for (status in listOf(PaymentGatewayStatus.READY, PaymentGatewayStatus.FAILED, PaymentGatewayStatus.PAY_PENDING)) {
            assertVerdict(
                MismatchType.PG_NOT_PAID_OURS_APPROVED,
                ReconciliationResolution.MANUAL_REQUIRED,
                payment(PaymentStatus.APPROVED),
                group(OrderGroupStatus.PAID),
                pg(status, paid = 0L),
            )
        }
        assertVerdict(
            MismatchType.PG_NOT_PAID_OURS_APPROVED,
            ReconciliationResolution.MANUAL_REQUIRED,
            payment(PaymentStatus.APPROVED),
            group(OrderGroupStatus.PAID),
            gateway = null,
        )
    }

    @Test
    fun `양쪽 다 미성사면 일치다`() {
        assertVerdict(
            MismatchType.MATCHED,
            ReconciliationResolution.NOT_REQUIRED,
            payment(PaymentStatus.PENDING),
            group(OrderGroupStatus.PENDING_PAYMENT),
            null,
        )
        assertVerdict(
            MismatchType.MATCHED,
            ReconciliationResolution.NOT_REQUIRED,
            payment(PaymentStatus.FAILED),
            group(OrderGroupStatus.CANCELLED),
            pg(PaymentGatewayStatus.FAILED, paid = 0L),
        )
    }

    @Test
    fun `PG 전액 취소이고 우리도 취소 반영이 끝났으면 일치다`() =
        assertVerdict(
            MismatchType.MATCHED,
            ReconciliationResolution.NOT_REQUIRED,
            payment(PaymentStatus.APPROVED, cancellable = 0L, cancelledAmount = amount),
            group(OrderGroupStatus.CANCELLED),
            pg(PaymentGatewayStatus.CANCELLED, cancelled = amount),
        )

    @Test
    fun `PG 취소를 우리 취소가능액이 모르면 사람 확인이다`() =
        assertVerdict(
            MismatchType.PG_CANCELLED_OURS_UNSYNCED,
            ReconciliationResolution.MANUAL_REQUIRED,
            payment(PaymentStatus.APPROVED),
            group(OrderGroupStatus.PAID),
            pg(PaymentGatewayStatus.CANCELLED, cancelled = amount),
        )

    @Test
    fun `PG 부분취소인데 그룹이 부분취소 상태가 아니면 사람 확인이다`() =
        assertVerdict(
            MismatchType.PG_CANCELLED_OURS_UNSYNCED,
            ReconciliationResolution.MANUAL_REQUIRED,
            payment(PaymentStatus.APPROVED, cancellable = 10_000L),
            group(OrderGroupStatus.PAID),
            pg(PaymentGatewayStatus.PARTIAL_CANCELLED, cancelled = 3_000L),
        )

    @Test
    fun `우리 결제 행이 없으면 PG에만 있는 건이다`() =
        assertVerdict(MismatchType.PG_ONLY_UNKNOWN, ReconciliationResolution.MANUAL_REQUIRED, null, null, pg(PaymentGatewayStatus.PAID))

    @Test
    fun `결제 행이 가리키는 그룹이 없으면 우리 원장 불일치다`() =
        assertVerdict(
            MismatchType.OURS_INCONSISTENT,
            ReconciliationResolution.MANUAL_REQUIRED,
            payment(PaymentStatus.PENDING),
            null,
            pg(PaymentGatewayStatus.PAID),
        )

    @Test
    fun `그룹은 결제완료인데 결제가 미승인이면 PG 대답과 무관하게 우리 원장 불일치다`() {
        assertVerdict(
            MismatchType.OURS_INCONSISTENT,
            ReconciliationResolution.MANUAL_REQUIRED,
            payment(PaymentStatus.PENDING),
            group(OrderGroupStatus.PAID),
            pg(PaymentGatewayStatus.PAID),
        )
        assertVerdict(
            MismatchType.OURS_INCONSISTENT,
            ReconciliationResolution.MANUAL_REQUIRED,
            payment(PaymentStatus.FAILED),
            group(OrderGroupStatus.PAID),
            null,
        )
    }

    @Test
    fun `승인 기록이 PG 승인액과 다르면 그룹이 결제대기여도 자동 보정하지 않는다`() =
        assertVerdict(
            MismatchType.AMOUNT_MISMATCH,
            ReconciliationResolution.MANUAL_REQUIRED,
            payment(PaymentStatus.APPROVED, paid = 12_000L),
            group(OrderGroupStatus.PENDING_PAYMENT),
            pg(PaymentGatewayStatus.PAID),
        )

    @Test
    fun `PG 전액 취소라도 원승인액이 다르면 취소 반영 일치가 아니라 금액 불일치다`() =
        assertVerdict(
            MismatchType.AMOUNT_MISMATCH,
            ReconciliationResolution.MANUAL_REQUIRED,
            payment(PaymentStatus.APPROVED, target = 20_000L, paid = 20_000L, cancellable = 0L),
            group(OrderGroupStatus.CANCELLED, finalAmount = 20_000L),
            pg(PaymentGatewayStatus.CANCELLED, paid = 10_000L, cancelled = 10_000L),
        )

    @Test
    fun `그룹이 부분취소인데 결제가 미승인이면 우리 원장 불일치다`() =
        assertVerdict(
            MismatchType.OURS_INCONSISTENT,
            ReconciliationResolution.MANUAL_REQUIRED,
            payment(PaymentStatus.PENDING),
            group(OrderGroupStatus.PARTIALLY_CANCELLED),
            null,
        )

    @Test
    fun `PG 부분취소와 우리 부분취소가 금액까지 맞으면 일치다`() =
        assertVerdict(
            MismatchType.MATCHED,
            ReconciliationResolution.NOT_REQUIRED,
            payment(PaymentStatus.APPROVED, cancellable = 10_000L, cancelledAmount = 3_000L),
            group(OrderGroupStatus.PARTIALLY_CANCELLED),
            pg(PaymentGatewayStatus.PARTIAL_CANCELLED, cancelled = 3_000L),
        )

    @Test
    fun `양쪽 승인이고 승인액은 같아도 취소가능액이 승인액과 다르면 금액 불일치다`() =
        assertVerdict(
            MismatchType.AMOUNT_MISMATCH,
            ReconciliationResolution.MANUAL_REQUIRED,
            payment(PaymentStatus.APPROVED, cancellable = 0L),
            group(OrderGroupStatus.PAID),
            pg(PaymentGatewayStatus.PAID),
        )

    @Test
    fun `취소 요청만 남고 PG에 반영되지 않았으면 끝나지 않은 취소로 사람 확인이다`() =
        assertVerdict(
            MismatchType.CANCEL_IN_FLIGHT,
            // 열린 상태로 남겨야 다음 회차가 이월해 다시 본다 — 일치로 접으면 선점이 영구히 남고 재취소가 막힌다
            ReconciliationResolution.MANUAL_REQUIRED,
            // 선점은 금액 원장을 건드리지 않으므로 취소가능액은 승인액 그대로다
            payment(PaymentStatus.CANCEL_REQUESTED),
            group(OrderGroupStatus.PAID),
            pg(PaymentGatewayStatus.PAID),
        )

    @Test
    fun `취소 요청 중인데 우리 잔액만 줄어 있으면 대기가 아니라 금액 불일치다`() =
        assertVerdict(
            MismatchType.AMOUNT_MISMATCH,
            ReconciliationResolution.MANUAL_REQUIRED,
            payment(PaymentStatus.CANCEL_REQUESTED, cancellable = 8_000L),
            group(OrderGroupStatus.PAID),
            pg(PaymentGatewayStatus.PAID),
        )

    @Test
    fun `부분취소가 반영된 뒤의 두 번째 취소 요청도 끝나지 않은 취소로 남는다`() =
        assertVerdict(
            MismatchType.CANCEL_IN_FLIGHT,
            ReconciliationResolution.MANUAL_REQUIRED,
            // 첫 부분취소 3,000원은 양쪽에 반영됐고, 두 번째 요청이 PG 응답을 기다리는 중이다
            payment(PaymentStatus.CANCEL_REQUESTED, cancellable = 10_000L, cancelledAmount = 3_000L),
            group(OrderGroupStatus.PARTIALLY_CANCELLED),
            pg(PaymentGatewayStatus.PARTIAL_CANCELLED, cancelled = 3_000L),
        )

    @Test
    fun `취소 요청 중인데 원승인액이 PG와 다르면 대기가 아니라 금액 불일치다`() =
        assertVerdict(
            MismatchType.AMOUNT_MISMATCH,
            ReconciliationResolution.MANUAL_REQUIRED,
            payment(PaymentStatus.CANCEL_REQUESTED),
            group(OrderGroupStatus.PAID),
            pg(PaymentGatewayStatus.PAID, paid = 12_000L),
        )

    @Test
    fun `취소 요청만 남았는데 PG는 이미 취소했으면 원장 미반영이라 사람 확인이다`() =
        assertVerdict(
            MismatchType.PG_CANCELLED_OURS_UNSYNCED,
            ReconciliationResolution.MANUAL_REQUIRED,
            payment(PaymentStatus.CANCEL_REQUESTED),
            group(OrderGroupStatus.PAID),
            pg(PaymentGatewayStatus.CANCELLED, cancelled = amount),
        )

    @Test
    fun `전액 취소가 원장과 그룹에 모두 반영됐으면 일치다`() =
        assertVerdict(
            MismatchType.MATCHED,
            ReconciliationResolution.NOT_REQUIRED,
            payment(PaymentStatus.CANCELLED, cancellable = 0L, cancelledAmount = amount),
            group(OrderGroupStatus.CANCELLED),
            pg(PaymentGatewayStatus.CANCELLED, cancelled = amount),
        )

    @Test
    fun `부분 취소가 원장과 그룹에 모두 반영됐으면 일치다`() =
        assertVerdict(
            MismatchType.MATCHED,
            ReconciliationResolution.NOT_REQUIRED,
            payment(PaymentStatus.PARTIAL_CANCELLED, cancellable = 10_000L, cancelledAmount = 3_000L),
            group(OrderGroupStatus.PARTIALLY_CANCELLED),
            pg(PaymentGatewayStatus.PARTIAL_CANCELLED, cancelled = 3_000L),
        )

    @Test
    fun `원장만 깎고 그룹을 전이하지 않았으면 사람 확인이다`() =
        assertVerdict(
            MismatchType.PG_CANCELLED_OURS_UNSYNCED,
            ReconciliationResolution.MANUAL_REQUIRED,
            payment(PaymentStatus.PARTIAL_CANCELLED, cancellable = 10_000L, cancelledAmount = 3_000L),
            group(OrderGroupStatus.PAID),
            pg(PaymentGatewayStatus.PARTIAL_CANCELLED, cancelled = 3_000L),
        )

    @Test
    fun `전액 취소가 끝난 결제를 PG가 결제완료로 말하면 금액 불일치다`() =
        assertVerdict(
            MismatchType.AMOUNT_MISMATCH,
            ReconciliationResolution.MANUAL_REQUIRED,
            payment(PaymentStatus.CANCELLED, cancellable = 0L, cancelledAmount = amount),
            group(OrderGroupStatus.PAID),
            pg(PaymentGatewayStatus.PAID),
        )

    @Test
    fun `남은 취소가능액은 맞는데 누적 취소액이 PG와 다르면 사람 확인이다`() =
        assertVerdict(
            // 두 컬럼이 함께 움직이지 않으면 부분취소 이력이 어긋난 것이다 — 취소가능액만 봐서는 가려지지 않는다
            MismatchType.PG_CANCELLED_OURS_UNSYNCED,
            ReconciliationResolution.MANUAL_REQUIRED,
            payment(PaymentStatus.PARTIAL_CANCELLED, cancellable = 10_000L, cancelledAmount = 1_000L),
            group(OrderGroupStatus.PARTIALLY_CANCELLED),
            pg(PaymentGatewayStatus.PARTIAL_CANCELLED, cancelled = 3_000L),
        )

    @Test
    fun `PG 승인 건도 누적 취소액이 어긋나면 금액 불일치다`() =
        assertVerdict(
            MismatchType.AMOUNT_MISMATCH,
            ReconciliationResolution.MANUAL_REQUIRED,
            payment(PaymentStatus.APPROVED, cancellable = amount, cancelledAmount = 3_000L),
            group(OrderGroupStatus.PAID),
            pg(PaymentGatewayStatus.PAID),
        )

    @Test
    fun `결제는 취소까지 갔는데 그룹이 결제대기면 우리 원장 불일치다`() =
        assertVerdict(
            // 환불 미반영(PG_CANCELLED_OURS_UNSYNCED)과 처방이 다르다 — 되돌릴 원장이 아니라 빠진 전이를 찾아야 한다
            MismatchType.OURS_INCONSISTENT,
            ReconciliationResolution.MANUAL_REQUIRED,
            payment(PaymentStatus.CANCELLED, cancellable = 0L, cancelledAmount = amount),
            group(OrderGroupStatus.PENDING_PAYMENT),
            pg(PaymentGatewayStatus.CANCELLED, cancelled = amount),
        )

    @Test
    fun `부분취소 그룹에 PG 전액취소가 붙으면 남은 주문이 취소 없이 환불된 것이라 사람 확인이다`() =
        assertVerdict(
            MismatchType.PG_CANCELLED_OURS_UNSYNCED,
            ReconciliationResolution.MANUAL_REQUIRED,
            payment(PaymentStatus.CANCELLED, cancellable = 0L, cancelledAmount = amount),
            group(OrderGroupStatus.PARTIALLY_CANCELLED),
            pg(PaymentGatewayStatus.CANCELLED, cancelled = amount),
        )
}
