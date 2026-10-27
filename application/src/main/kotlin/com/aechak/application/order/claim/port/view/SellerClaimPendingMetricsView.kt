package com.aechak.application.order.claim.port.view

/**
 * 셀러 처리 대기 클레임 집계 한 묶음 — 단계마다 쿼리를 따로 돌지 않도록 한 번에 받는다.
 * 요청 대기는 유형별로 갈리고(승인·거부 판단이 유형마다 다른 화면이다), 회수·재발송 대기는
 * 유형이 아니라 **셀러가 다음에 할 일**로 갈린다.
 */
data class SellerClaimPendingMetricsView(
    val cancelRequests: Long,
    val returnRequests: Long,
    val exchangeRequests: Long,
    val collectionInvoicePending: Long,
    val collectionCompletionPending: Long,
    val reshipPending: Long,
)
