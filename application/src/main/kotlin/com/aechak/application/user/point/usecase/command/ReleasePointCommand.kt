package com.aechak.application.user.point.usecase.command

/**
 * 쓰기 도메인(주문 등)이 넘기는 적립금 복원 명세.
 * 멱등키는 복원 단위마다 달라야 한다 — 주문그룹 전체 복원과 부분취소 복원이 같은 키를 쓰면 둘째 복원이 조용히 사라진다.
 */
data class ReleasePointCommand(
    val userId: Long,
    val amount: Long,
    val idempotencyKey: String,
    val sourceType: String? = null,
    val sourceId: Long? = null,
)
