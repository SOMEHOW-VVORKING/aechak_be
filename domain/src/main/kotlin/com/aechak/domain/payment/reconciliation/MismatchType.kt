package com.aechak.domain.payment.reconciliation

/** PG 기록과 우리 원장을 대조한 결과. 이름은 "PG 쪽 사실 + 우리 쪽 사실" 순서로 읽는다 */
enum class MismatchType {
    /** 상태·금액이 전부 일치하거나, 양쪽 다 미성사 */
    MATCHED,

    /** PG는 승인인데 우리는 아직 확정하지 않음 — 콜백·웹훅을 둘 다 놓친 건. 확정 본체로 자동 보정 */
    PG_PAID_OURS_UNCONFIRMED,

    /** PG는 승인인데 우리 주문그룹은 취소됨 — 돈만 나간 건. 환불 필요 */
    PG_PAID_GROUP_CANCELLED,

    /** 양쪽 다 승인이지만 주문금액·등록금액·실결제액 중 하나라도 다름 */
    AMOUNT_MISMATCH,

    /** 우리는 승인인데 PG는 미성사·없음 — 돈을 못 받고 발송될 수 있는 가장 위험한 건 */
    PG_NOT_PAID_OURS_APPROVED,

    /** PG는 (부분)취소인데 우리 취소가능액이 그 취소를 모름 */
    PG_CANCELLED_OURS_UNSYNCED,

    /** PG에는 있는데 우리 결제 행이 없음 */
    PG_ONLY_UNKNOWN,

    /** 우리 원장끼리 어긋남 — 결제 행이 가리키는 그룹이 없거나, 그룹은 결제완료인데 결제는 미승인인 조합 */
    OURS_INCONSISTENT,

    /** 취소 선점만 남고 PG에 반영되지 않음 — 요청이 끝나지 않은 건. 선점이 남아 재취소도 막힌다 */
    CANCEL_IN_FLIGHT,
}
