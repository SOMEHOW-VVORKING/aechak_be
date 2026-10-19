package com.aechak.application.product.product.port

import com.aechak.application.product.product.port.view.SellerProductOptionView
import com.aechak.application.product.product.port.view.SellerProductOwnershipView
import com.aechak.application.product.product.port.view.SellerProductView

/** 셀러 자신의 상품 목록·옵션 재고 조회 포트 — 검수·판매 상태 무관 전체 조회 */
interface SellerProductQueryPort {
    fun findPage(condition: SellerProductCondition): List<SellerProductView>

    fun count(condition: SellerProductCondition): Long

    /** 소유권 판정용 최소 조회 — 노출 조건 없이 publicId를 해석한다 */
    fun findOwnership(publicId: String): SellerProductOwnershipView?

    /** 조합별 재고 — 비활성 조합 포함 */
    fun findCombinations(productId: Long): List<SellerProductOptionView>
}
