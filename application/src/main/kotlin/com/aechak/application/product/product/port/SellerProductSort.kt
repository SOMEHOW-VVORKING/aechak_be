package com.aechak.application.product.product.port

/** 셀러 상품 목록 정렬 — 가격은 조회 시각의 유효가(할인 반영가) 기준 */
enum class SellerProductSort {
    LATEST,
    PRICE_ASC,
    PRICE_DESC,
}
