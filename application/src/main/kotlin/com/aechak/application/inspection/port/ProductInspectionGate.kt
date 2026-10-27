package com.aechak.application.inspection.port

import com.aechak.domain.inspection.enums.InspectionJudgment

/**
 * 판정을 상품 노출에 반영하는 포트.
 *
 * 검수 쪽이 상품 저장소를 직접 알지 않게 가른다. 검수는 "막아라·되돌려라"까지만 말하고,
 * 그것이 어떤 상태 전이인지는 상품 쪽이 정한다. 대상이 늘어도 판정 적용부는 그대로 두고
 * 구현만 늘린다.
 *
 * 세 메서드 모두 상품 행을 잠금. 호출부는 같은 트랜잭션에서 검토 큐를 고쳐야 상품마다 열린 행이 하나로 유지됨.
 */
interface ProductInspectionGate {
    /**
     * 노출 게이트 스위치가 꺼졌거나 상품이 없으면 null.
     * 잠근 뒤에 보니 최신 검수의 판정이 아니면 아무것도 하지 않고 null.
     *
     * [isLatestJudgment]와 [isAdminRejected]는 둘 다 상품 행을 잠근 뒤에 부름. 잠그기 전에
     * 읽으면 잠금을 기다리는 사이에 커밋된 새 검수와 관리자 결정을 못 봄.
     *
     * 둘 다 비잠금 조회라서 호출부 트랜잭션이 READ COMMITTED여야 뜻이 있음. 기본 격리에서는
     * 읽기 뷰가 트랜잭션의 첫 일관 읽기에서 고정되어, 잠금을 받은 뒤에 읽어도 그 사이 커밋된
     * 것이 보이지 않음
     */
    fun applyJudgment(
        productId: Long,
        judgment: InspectionJudgment,
        isLatestJudgment: () -> Boolean,
        isAdminRejected: () -> Boolean,
    ): ProductGateOutcome?

    fun approveByAdmin(productId: Long)

    fun rejectByAdmin(productId: Long)
}
