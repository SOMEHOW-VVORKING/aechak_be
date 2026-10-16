package com.aechak.application.payment.reconciliation.service

import com.aechak.application.payment.port.GatewayPaymentRecord
import com.aechak.application.payment.port.PaymentGatewayStatus
import com.aechak.domain.order.group.OrderGroup
import com.aechak.domain.order.group.enums.OrderGroupStatus
import com.aechak.domain.payment.Payment
import com.aechak.domain.payment.enums.PaymentStatus
import com.aechak.domain.payment.reconciliation.MismatchType
import com.aechak.domain.payment.reconciliation.ReconciliationResolution

/**
 * 대사 판정표 — 순수 함수. 입력은 우리 결제·주문그룹과 PG 기록, 출력은 불일치 유형과 처리 방향.
 *
 * 원칙 둘:
 * 1. 자동 처리는 "PG 쪽 사실을 우리에게 반영하는" 방향뿐이고, 그것도 기존 확정 본체(재조회·3중 대조·선점)를 태운다.
 * 2. 돈이 걸린 나머지(금액 불일치·우리만 승인·PG 취소 미반영·모르는 결제·취소된 그룹의 승인)는 사람 몫이다.
 *
 * 우선순위: 결제 행 없음 → 우리 원장 내부 불일치 → (PG 승인) 그룹 취소 → 금액 → 상태.
 * 우리 결제가 이미 승인이면 어느 분기에서든 승인 원장(등록금액·실결제액)이 PG 승인액과 같아야 한다 — 다르면 자동 보정으로 보내지 않는다.
 */
object PaymentReconciliationClassifier {
    data class Verdict(
        val mismatchType: MismatchType,
        val resolution: ReconciliationResolution,
    )

    fun classify(
        payment: Payment?,
        group: OrderGroup?,
        gateway: GatewayPaymentRecord?,
    ): Verdict {
        if (payment == null) return manual(MismatchType.PG_ONLY_UNKNOWN)
        // 결제 행은 항상 그룹을 가리킨다 — 없으면 PG와 무관하게 우리 데이터가 깨진 것
        if (group == null) return manual(MismatchType.OURS_INCONSISTENT)
        val oursApproved = payment.status == PaymentStatus.APPROVED
        // 결제완료 이후 상태(PAID·부분취소)의 그룹에 결제 행이 미승인이면 확정 트랜잭션이 반만 남은 것 — PG 대답과 무관하게 사람이 봐야 한다
        val groupSettled = group.status == OrderGroupStatus.PAID || group.status == OrderGroupStatus.PARTIALLY_CANCELLED
        if (groupSettled && !oursApproved) return manual(MismatchType.OURS_INCONSISTENT)

        return when (gateway?.status) {
            PaymentGatewayStatus.PAID -> {
                classifyPaid(payment, group, gateway, oursApproved)
            }

            PaymentGatewayStatus.CANCELLED, PaymentGatewayStatus.PARTIAL_CANCELLED -> {
                classifyCancelled(payment, group, gateway, oursApproved)
            }

            // READY·FAILED·승인 대기·가상계좌 발급·PG에 없음 — 전부 "돈이 안 들어온 상태"
            else -> {
                if (oursApproved) manual(MismatchType.PG_NOT_PAID_OURS_APPROVED) else matched()
            }
        }
    }

    private fun classifyPaid(
        payment: Payment,
        group: OrderGroup,
        gateway: GatewayPaymentRecord,
        oursApproved: Boolean,
    ): Verdict {
        if (group.status == OrderGroupStatus.CANCELLED || group.status == OrderGroupStatus.PARTIALLY_CANCELLED) {
            return manual(MismatchType.PG_PAID_GROUP_CANCELLED)
        }
        if (oursApproved) {
            // 승인 원장이 PG와 다르면 그룹 상태와 무관하게 사람 확인 — 확정 본체의 approve()는 이미 승인된 행을 고치지 않는다
            if (!approvedLedgerAgrees(group, payment, gateway)) return manual(MismatchType.AMOUNT_MISMATCH)
            if (group.status == OrderGroupStatus.PAID) return matched()
        }
        // 그룹이 결제대기 — 둘 다 못 들었거나 승인 기록만 남고 전이가 유실된 것. 확정 본체가 다시 PG에 묻고 금액을 대조한 뒤 선점한다
        return Verdict(MismatchType.PG_PAID_OURS_UNCONFIRMED, ReconciliationResolution.AUTO_PENDING)
    }

    private fun classifyCancelled(
        payment: Payment,
        group: OrderGroup,
        gateway: GatewayPaymentRecord,
        oursApproved: Boolean,
    ): Verdict {
        // 승인 기록도 없는데 PG는 취소까지 갔다 — 승인·취소 통보를 전부 놓친 것
        if (!oursApproved) return manual(MismatchType.PG_CANCELLED_OURS_UNSYNCED)
        // 취소 전 원승인액부터 같아야 취소액 비교가 의미 있다
        if (!approvedLedgerAgrees(group, payment, gateway)) return manual(MismatchType.AMOUNT_MISMATCH)
        val cancellableAgrees = payment.cancellableAmount == gateway.paidAmount - gateway.cancelledAmount
        val groupAgrees =
            when (gateway.status) {
                PaymentGatewayStatus.CANCELLED -> group.status == OrderGroupStatus.CANCELLED
                else -> group.status == OrderGroupStatus.PARTIALLY_CANCELLED
            }
        return if (cancellableAgrees && groupAgrees) matched() else manual(MismatchType.PG_CANCELLED_OURS_UNSYNCED)
    }

    /** 주문금액·등록금액·우리 실결제액·PG 승인액이 전부 같아야 한다 — 하나라도 어긋나면 사람 확인 */
    private fun approvedLedgerAgrees(
        group: OrderGroup,
        payment: Payment,
        gateway: GatewayPaymentRecord,
    ): Boolean =
        group.finalPaymentAmount == payment.targetAmount &&
            payment.targetAmount == gateway.paidAmount &&
            payment.realPaidAmount == gateway.paidAmount

    private fun matched() = Verdict(MismatchType.MATCHED, ReconciliationResolution.NOT_REQUIRED)

    private fun manual(type: MismatchType) = Verdict(type, ReconciliationResolution.MANUAL_REQUIRED)
}
