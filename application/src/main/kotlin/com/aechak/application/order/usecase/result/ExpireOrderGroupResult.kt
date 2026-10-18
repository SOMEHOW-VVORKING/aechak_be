package com.aechak.application.order.usecase.result

/** 만료 확정의 결과 — 복원 수치는 운영 로그용이다 */
data class ExpireOrderGroupResult(
    val status: ExpireOrderGroupStatus,
    val cancelledOrders: Int = 0,
    val restoredStockItems: Int = 0,
    val failedStockItems: Int = 0,
    val releasedPoint: Long = 0,
) {
    companion object {
        fun of(status: ExpireOrderGroupStatus): ExpireOrderGroupResult = ExpireOrderGroupResult(status)
    }
}

enum class ExpireOrderGroupStatus {
    /** 이번 호출이 취소 전이를 선점해 재고·적립금을 되돌림 */
    EXPIRED,

    /** 스캔 시점과 달리 만료 시각이 지나지 않음(연장 등) — 아무것도 하지 않음 */
    NOT_EXPIRED,

    /** 결제 확정·앞선 회차가 이미 상태를 정했음 — 복원하지 않음(멱등) */
    ALREADY_SETTLED,

    /** 스캔 이후 사라진 그룹 */
    NOT_FOUND,
}
