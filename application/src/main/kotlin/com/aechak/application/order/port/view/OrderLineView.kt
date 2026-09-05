package com.aechak.application.order.port.view

import com.aechak.domain.order.order.enums.OrderItemStatus

/**
 * 상품명과 썸네일은 주문 시점 product_versions 스냅샷, 옵션명은 option_combinations 현재값.
 * 옵션명 스냅샷 컬럼이 없어 셀러가 옵션을 개명하면 지난 주문의 표기도 따라 바뀜.
 */
data class OrderLineView(
    val orderId: Long,
    val productName: String,
    val thumbnailKey: String,
    val optionName: String,
    val quantity: Int,
    val unitPrice: Long,
    val itemStatus: OrderItemStatus,
)
