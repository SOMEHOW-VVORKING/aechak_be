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
    /** 노출 게이트 스위치가 꺼졌거나 상품이 없으면 null */
    fun applyJudgment(
        productId: Long,
        judgment: InspectionJudgment,
    ): ProductGateOutcome?

    fun approveByAdmin(productId: Long)

    fun rejectByAdmin(productId: Long)
}
