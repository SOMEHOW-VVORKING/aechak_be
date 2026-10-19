package com.aechak.domain.settlement.batch.enums

enum class SettlementBatchStatus {
    GENERATED,
    PROCESSING,
    COMPLETED,
    ;

    fun canTransitionTo(next: SettlementBatchStatus): Boolean =
        next in
            when (this) {
                GENERATED -> setOf(PROCESSING)
                PROCESSING -> setOf(COMPLETED)
                COMPLETED -> emptySet()
            }
}
