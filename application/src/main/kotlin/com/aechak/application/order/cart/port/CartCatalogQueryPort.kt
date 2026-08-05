package com.aechak.application.order.cart.port

import com.aechak.application.order.cart.port.view.CartCatalogItemView

/** 담기 시점 검증용. 상품과 셀러는 옵션 조합에서 파생함. */
interface CartCatalogQueryPort {
    fun findItem(optionCombinationId: Long): CartCatalogItemView?
}
