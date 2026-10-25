package com.aechak.application.inspection.port

import com.aechak.domain.inspection.enums.InspectionJudgment

/**
 * 판정을 상품 노출에 반영하는 포트.
 *
 * 검수 쪽이 상품 저장소를 직접 알지 않게 가른다. 검수는 "막아라·되돌려라"까지만 말하고,
 * 그것이 어떤 상태 전이인지는 상품 쪽이 정한다. 대상이 늘어도 판정 적용부는 그대로 두고
 * 구현만 늘린다.
 *
 * 네 메서드 모두 상품 행을 잠금. 호출부는 같은 트랜잭션에서 검토 큐를 고쳐야 상품마다 열린 행이 하나로 유지됨.
 */
interface ProductInspectionGate {
    /**
     * 노출 게이트 스위치가 꺼졌거나 상품이 없으면 null
     *
     * [modelVerified]는 통과 판정을 뒷받침하는 증거가 있는지다. 통과라고 말해 놓고 그 통과를
     * 뒷받침하는 관점이 하나도 없는 판정은 받아들이면 아무도 보지 않은 상품이 노출된다.
     */
    fun applyJudgment(
        productId: Long,
        judgment: InspectionJudgment,
        modelVerified: Boolean,
    ): ProductGateOutcome?

    fun approveByAdmin(productId: Long)

    fun rejectByAdmin(productId: Long)

    /**
     * 이의 접수가 쓰는 잠금. 상태를 바꾸지 않고 상품 행만 잠근다. 상품이 없으면 null
     *
     * 다른 셋과 달리 노출 게이트 스위치를 보지 않는다. 관리자 결정과 같은 사람 경로이고,
     * 스위치에 묶이면 스위치가 꺼진 환경에서 이의가 조용히 아무것도 하지 않는다.
     */
    fun lockForAppeal(productId: Long): ProductAppealTarget?
}
