package com.aechak.domain.payment.reconciliation.repository

import com.aechak.domain.payment.reconciliation.PaymentReconciliationRun
import com.aechak.domain.payment.reconciliation.ReconciliationSource
import java.time.LocalDate

interface PaymentReconciliationRunRepository {
    /** 신규는 INSERT, 기존 id는 낙관적 락(version)으로 갱신. (source, base_date) UNIQUE 충돌·버전 충돌은 그대로 던진다 */
    fun save(run: PaymentReconciliationRun): PaymentReconciliationRun

    fun findBySourceAndBaseDate(
        source: ReconciliationSource,
        baseDate: LocalDate,
    ): PaymentReconciliationRun?

    fun findById(id: Long): PaymentReconciliationRun?

    /**
     * 쓰기 트랜잭션용 — 행 잠금(SELECT ... FOR UPDATE)으로 읽는다. 이 트랜잭션이 끝날 때까지 다른 실행의 이어받기(run UPDATE)가
     * 기다리므로, 잠근 뒤 확인한 소유권은 같은 트랜잭션 안의 건 쓰기까지 유효하다.
     */
    fun findByIdForUpdate(id: Long): PaymentReconciliationRun?

    /** 따라잡기의 출발점 — 완료된 회차 중 가장 늦은 기준일. 없으면 null */
    fun findLatestCompletedBaseDate(source: ReconciliationSource): LocalDate?

    /** 따라잡기가 다시 봐야 할 날 — 기간 안에서 완료되지 않은(FAILED·RUNNING) 회차의 기준일 */
    fun findIncompleteBaseDates(
        source: ReconciliationSource,
        from: LocalDate,
        until: LocalDate,
    ): List<LocalDate>
}
