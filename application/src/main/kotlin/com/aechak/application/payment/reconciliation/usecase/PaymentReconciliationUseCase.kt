package com.aechak.application.payment.reconciliation.usecase

import com.aechak.application.payment.reconciliation.usecase.command.CatchUpReconciliationCommand
import com.aechak.application.payment.reconciliation.usecase.command.ReconcilePaymentsCommand
import com.aechak.application.payment.reconciliation.usecase.result.ReconcilePaymentsResult

/**
 * 결제 대사 진입점 — 배치 스케줄러가 부른다.
 * 같은 (소스, 기준일)을 몇 번 불러도 완료된 회차는 SKIPPED로 끝나고, 실패했거나 죽은 회차만 이어받는다.
 */
interface PaymentReconciliationUseCase {
    fun reconcile(command: ReconcilePaymentsCommand): ReconcilePaymentsResult

    /** 빠진 날짜를 순서대로 메운다. 한 날이 FAILED면 그 뒤는 돌리지 않는다(소스 장애를 날짜 수만큼 반복하지 않으려고) */
    fun catchUp(command: CatchUpReconciliationCommand): List<ReconcilePaymentsResult>
}
