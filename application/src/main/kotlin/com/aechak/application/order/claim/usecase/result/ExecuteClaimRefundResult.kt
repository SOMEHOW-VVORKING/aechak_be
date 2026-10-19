package com.aechak.application.order.claim.usecase.result

enum class ExecuteClaimRefundStatus {
    /** PG가 취소를 확정하고 원장·주문그룹·자산 복원까지 끝났다 */
    REFUNDED,

    /** PG가 비동기로 접수만 했다 — 원장과 주문그룹은 보류하고 클레임은 요청 상태로 남는다 */
    REFUND_REQUESTED,

    /** 집행 대상이 아니다 — 이미 집행됐거나, 승인 상태가 아니거나, 환불액이 없어 실패로 기록했다 */
    SKIPPED,
}

data class ExecuteClaimRefundResult(
    val status: ExecuteClaimRefundStatus,
    /** PG로 요청한 환불액. 집행하지 않은 건은 0 */
    val refundAmount: Long,
    /** 되돌린 적립금. 적립금을 쓰지 않은 주문은 0 */
    val releasedPoint: Long,
    /** 되돌리지 못해 건너뛴 옵션조합 수 — 0이 아니면 재고를 사람이 맞춰야 한다 */
    val skippedStockItems: Int,
) {
    companion object {
        fun skipped(): ExecuteClaimRefundResult = ExecuteClaimRefundResult(ExecuteClaimRefundStatus.SKIPPED, 0L, 0L, 0)

        fun requested(refundAmount: Long): ExecuteClaimRefundResult =
            ExecuteClaimRefundResult(ExecuteClaimRefundStatus.REFUND_REQUESTED, refundAmount, 0L, 0)
    }
}
