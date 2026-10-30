package com.aechak.application.order.admin.usecase.result

/**
 * 운영자가 먼저 봐야 하는 지연 신호의 종류. 판정은 서버가 한다 — 화면이 날짜를 빼서 세면
 * 기준이 화면마다 갈리고 지표 카드와 목록 행이 서로 다른 건수를 말한다.
 */
enum class AdminOrderDelayReason {
    /** 발송 기한이 지났는데 아직 송장이 붙지 않았다. */
    DISPATCH_OVERDUE,

    /** 배송중인 채로 배송 기한이 지났다. */
    DELIVERY_OVERDUE,
}
