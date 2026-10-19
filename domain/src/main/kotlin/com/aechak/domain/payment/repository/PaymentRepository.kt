package com.aechak.domain.payment.repository

import com.aechak.domain.payment.Payment
import java.time.LocalDateTime

interface PaymentRepository {
    fun save(payment: Payment): Payment

    fun findByOrderGroupId(orderGroupId: Long): Payment?

    /** 웹훅이 알려주는 식별자로 찾는 경로. paymentId는 UNIQUE라 결과는 0 또는 1행 */
    fun findByPaymentId(paymentId: String): Payment?

    /**
     * 대사용 — 창 안에서 상태가 바뀐(=updated_at이 든) 결제 식별자. PG 목록에 없는 "우리만 있는 건"을 찾는 재료.
     * 상태 변경은 모두 저장을 거쳐 updated_at을 갱신하므로 이 창이 곧 우리 쪽 상태 변경 창이다.
     */
    fun findPaymentIdsUpdatedBetween(
        from: LocalDateTime,
        until: LocalDateTime,
    ): List<String>
}
