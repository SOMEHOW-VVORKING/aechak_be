package com.aechak.application.inquiry.product.usecase.query

/** 상품 문의 목록의 페이지 크기 경계 — 세 목록이 같은 상한을 쓴다. */
object ProductInquiryPageSize {
    const val DEFAULT = 20

    // @Range(min, max) 속성이 long이라 Long으로 둔다 — Int는 컴파일 불가
    const val MIN = 1L
    const val MAX = 100L
}
