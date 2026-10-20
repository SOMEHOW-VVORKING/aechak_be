package com.aechak.application.product.product.port.view

/**
 * 셀러 상품의 상태별 건수. 네 값은 서로 배타적이지 않다 —
 * 판매 중지된 상품의 재고가 0이면 중지와 품절에 함께 센다.
 */
data class SellerProductStatusCountView(
    val awaitingInspection: Long,
    val rejected: Long,
    val suspended: Long,
    val soldOut: Long,
)
