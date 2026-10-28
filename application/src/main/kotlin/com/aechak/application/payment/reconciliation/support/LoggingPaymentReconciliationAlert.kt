package com.aechak.application.payment.reconciliation.support

import com.aechak.application.payment.port.PaymentReconciliationAlertPort
import com.aechak.domain.payment.reconciliation.PaymentReconciliationItem
import com.aechak.domain.payment.reconciliation.PaymentReconciliationRun
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * 기본 알림 — 로그. 사람이 봐야 하는 건이 있으면 ERROR 한 줄(알람 훅) + 건별 WARN, 없으면 INFO 한 줄.
 * 메일·메신저로 보내려면 이 포트의 다른 어댑터를 실행 모듈에서 @Primary로 올린다.
 */
@Component
class LoggingPaymentReconciliationAlert : PaymentReconciliationAlertPort {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun notifyRunFinished(
        run: PaymentReconciliationRun,
        attentionItems: List<PaymentReconciliationItem>,
    ) {
        val c = run.counts
        val summary =
            "source=${run.source}, baseDate=${run.baseDate}, attempt=${run.attempt}, status=${run.status}, " +
                "fetched=${c.fetched}, compared=${c.compared}, matched=${c.matched}, auto=${c.autoResolved}, " +
                "manual=${c.manualRequired}, failed=${c.failed}"
        if (attentionItems.isEmpty()) {
            log.info("결제 대사 완료. {}", summary)
            return
        }
        log.error("결제 대사 — 사람 확인 필요 {}건. {}", attentionItems.size, summary)
        attentionItems.forEach { item ->
            log.warn(
                "결제 대사 불일치. paymentKey={}, type={}, resolution={}, ours={}/{}/{}원, pg={}/{}원, note={}",
                item.paymentKey,
                item.mismatchType,
                item.resolution,
                item.ours?.paymentStatus,
                item.ours?.groupStatus,
                item.ours?.paidAmount ?: item.ours?.targetAmount,
                item.gateway?.status,
                item.gateway?.paidAmount,
                item.resolutionNote,
            )
        }
    }
}
