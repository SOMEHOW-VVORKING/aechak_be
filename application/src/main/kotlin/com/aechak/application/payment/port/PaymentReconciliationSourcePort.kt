package com.aechak.application.payment.port

import com.aechak.domain.payment.reconciliation.ReconciliationSource
import java.time.LocalDateTime

/**
 * 대사가 PG에서 결제 기록을 받아오는 창구. PG마다 어댑터가 하나씩이고 소스 이름으로 구분한다.
 * 포트원은 결제 식별자(paymentId)가, 토스는 주문번호(orderId)가 우리 주문그룹 publicId와 같다는 전제로 paymentKey를 채운다.
 */
interface PaymentReconciliationSourcePort {
    val source: ReconciliationSource

    /**
     * 이 소스가 우리 결제 원장 전체를 설명하는가. true면 "우리 쪽은 바뀌었는데 PG 목록에 없는 건"을 단건으로 다시 묻는다.
     * 일부 결제만 다루는 소스(예: 포트원 채널 옆에 둔 토스 직접 연동)가 그 조회를 하면 자기 몫이 아닌 결제를 전부 '없음'으로 오판한다.
     */
    val coversInternalLedger: Boolean

    /**
     * 이 소스가 보고한 승인을 확정 본체로 자동 보정해도 되는가. 확정 본체는 PaymentGatewayPort(포트원)를 다시 묻는다 —
     * 다른 PG의 승인을 포트원에 물으면 '없음'이 나오므로 그런 소스의 승인은 자동 보정 대신 사람 확인으로 남긴다.
     */
    val supportsAutoConfirmation: Boolean

    /**
     * 상태가 바뀐 시각이 [from, until)에 드는 결제 전부. 페이지 순회는 어댑터 안에서 끝낸다.
     * 창을 넘는 건은 포함하지 않는다 — 어제 승인됐다가 오늘 취소된 건은 "오늘" 창에서 취소로 보인다.
     */
    fun fetchChangedPayments(
        from: LocalDateTime,
        until: LocalDateTime,
    ): List<GatewayPaymentRecord>

    /** 단건 재확인. PG가 모르는 식별자면 null */
    fun findByPaymentKey(paymentKey: String): GatewayPaymentRecord?
}

/** PG 결제 기록을 우리 어휘로 옮긴 것. 금액은 원 단위 정수 */
data class GatewayPaymentRecord(
    val paymentKey: String,
    val status: PaymentGatewayStatus,
    val totalAmount: Long,
    val paidAmount: Long,
    val cancelledAmount: Long,
    val pgTxId: String?,
    val statusChangedAt: LocalDateTime?,
)
