package com.aechak.application.order.claim.usecase.result

enum class AutoApproveClaimStatus {
    /** 시스템이 승인했다 — 환불 집행은 아직 일어나지 않았고 refundStatus는 NONE으로 남는다 */
    APPROVED,

    /** 자동승인 대상이 아니다 — 셀러가 먼저 처리했거나 구매자가 철회했거나 기한이 아직 남았다 */
    SKIPPED,
}

data class AutoApproveClaimResult(
    val status: AutoApproveClaimStatus,
) {
    companion object {
        fun approved(): AutoApproveClaimResult = AutoApproveClaimResult(AutoApproveClaimStatus.APPROVED)

        fun skipped(): AutoApproveClaimResult = AutoApproveClaimResult(AutoApproveClaimStatus.SKIPPED)
    }
}
