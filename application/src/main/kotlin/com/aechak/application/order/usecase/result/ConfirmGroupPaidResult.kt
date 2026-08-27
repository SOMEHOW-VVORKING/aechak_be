package com.aechak.application.order.usecase.result

/** 결제 확정 선점의 결과 — 진 쪽은 현재 상태를 보고 행동을 정한다. */
enum class ConfirmGroupPaidResult {
    /** 이번 호출이 선점에 성공해 그룹·주문을 결제완료로 전이함 */
    CONFIRMED,

    /** 다른 입구(콜백·웹훅 경쟁)가 이미 확정함 — 멱등 성공으로 처리 */
    ALREADY_PAID,

    /** 만료 등으로 이미 취소됨 — 돈이 나갔다면 환불이 필요한 사건 */
    ALREADY_CANCELLED,
}
