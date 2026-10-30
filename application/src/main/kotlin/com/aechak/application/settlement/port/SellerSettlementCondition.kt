package com.aechak.application.settlement.port

/**
 * 셀러 정산 목록 조회 조건.
 *
 * - sellerId로 잠가 본인 정산만 조회한다
 * - settlementMonth가 null이면 월 조건 미적용(전체), status가 null이면 상태 조건 미적용
 * - status는 접힌 상태로 걸린다 — 주문 단위로 접은 결과가 그 값이어야 통과한다
 */
data class SellerSettlementCondition(
    val sellerId: Long,
    val settlementMonth: String?,
    val status: SellerSettlementStatus?,
    val offset: Long,
    val limit: Int,
) {
    init {
        require(offset >= 0) { "offset은 0 이상이어야 합니다." }
        require(limit > 0) { "limit은 양수여야 합니다." }
    }
}
