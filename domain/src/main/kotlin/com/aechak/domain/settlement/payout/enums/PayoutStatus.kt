package com.aechak.domain.settlement.payout.enums

enum class PayoutStatus {
    PENDING,
    SENT,
    PAID,
    FAILED,
    ;

    fun canTransitionTo(next: PayoutStatus): Boolean =
        next in
            when (this) {
                PENDING -> setOf(SENT, FAILED)

                SENT -> setOf(PAID, FAILED)

                // 실패한 송금은 같은 transfer_key로 다시 보낸다 — transfer_key UNIQUE 때문에 새 Payout을 만들 수 없다.
                FAILED -> setOf(SENT)

                PAID -> emptySet()
            }
}
