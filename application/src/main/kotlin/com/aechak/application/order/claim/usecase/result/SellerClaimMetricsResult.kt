package com.aechak.application.order.claim.usecase.result

import com.aechak.application.order.claim.port.view.SellerClaimPendingMetricsView

/**
 * 셀러가 아직 해야 할 일이 남은 클레임 건수. 요청 대기는 유형별로, 승인 이후 남은 일은 단계별로 센다 —
 * 승인하면 요청 건수에서 빠지지만 셀러의 할 일은 회수 송장 등록·회수 완료 처리·교환 재발송으로 이어진다.
 */
data class SellerClaimMetricsResult(
    val cancelRequests: Long,
    val returnRequests: Long,
    val exchangeRequests: Long,
    val collectionInvoicePending: Long,
    val collectionCompletionPending: Long,
    val reshipPending: Long,
) {
    companion object {
        fun from(view: SellerClaimPendingMetricsView): SellerClaimMetricsResult =
            SellerClaimMetricsResult(
                cancelRequests = view.cancelRequests,
                returnRequests = view.returnRequests,
                exchangeRequests = view.exchangeRequests,
                collectionInvoicePending = view.collectionInvoicePending,
                collectionCompletionPending = view.collectionCompletionPending,
                reshipPending = view.reshipPending,
            )
    }
}
