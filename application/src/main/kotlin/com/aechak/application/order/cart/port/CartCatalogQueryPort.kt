package com.aechak.application.order.cart.port

import com.aechak.application.order.cart.port.view.CartCatalogItemView

/** 상품과 셀러는 옵션 조합에서 파생함. */
interface CartCatalogQueryPort {
    /** 담기 시점 검증용 단건 */
    fun findItem(optionCombinationId: Long): CartCatalogItemView?

    /** 조회 화면용 복수 */
    fun findItems(optionCombinationIds: Collection<Long>): List<CartCatalogItemView>

    /** 수정 시점 검증용 복수 */
    fun findApprovedItems(optionCombinationIds: Collection<Long>): List<CartCatalogItemView>

    /** 개수 조회용. 조회 화면에 실리는 항목만 세고, 수량 합계가 아니라 라인 수 */
    fun countDisplayableItems(buyerId: Long): Int
}
