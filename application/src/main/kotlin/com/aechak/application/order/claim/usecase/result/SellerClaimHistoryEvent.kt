package com.aechak.application.order.claim.usecase.result

/** 클레임 진행 이력의 사건 어휘. 선언 순서가 같은 시각에 겹친 사건의 표시 순서다. */
enum class SellerClaimHistoryEvent {
    REQUESTED,
    COLLECTION_STARTED,
    APPROVED,
    CLAWBACK_DEPOSITED,
    REJECTED,
    WITHDRAWN,
    ADMIN_OVERRIDDEN,
    REFUNDED,
    RESOLVED,
}
