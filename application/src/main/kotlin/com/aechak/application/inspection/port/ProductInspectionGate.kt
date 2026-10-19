package com.aechak.application.inspection.port

/**
 * 판정을 상품 노출에 반영하는 포트.
 *
 * 검수 쪽이 상품 저장소를 직접 알지 않게 가른다. 검수는 "막아라·되돌려라"까지만 말하고,
 * 그것이 어떤 상태 전이인지는 상품 쪽이 정한다. 대상이 늘어도 판정 적용부는 그대로 두고
 * 구현만 늘린다.
 */
interface ProductInspectionGate {
    fun rejectByInspection(productId: Long)

    fun approveByInspection(productId: Long)
}
