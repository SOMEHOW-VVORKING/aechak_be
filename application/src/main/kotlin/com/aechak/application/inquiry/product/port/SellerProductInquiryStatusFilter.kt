package com.aechak.application.inquiry.product.port

import com.aechak.domain.inquiry.product.enums.ProductInquiryStatus

/**
 * 셀러 목록의 상태 탭. 도메인 상태를 그대로 쓰지 않는 이유 —
 * 철회(WITHDRAWN)는 셀러 목록에 아예 나오지 않으므로 고를 수 있는 값이 아니다.
 */
enum class SellerProductInquiryStatusFilter(
    val status: ProductInquiryStatus,
) {
    RECEIVED(ProductInquiryStatus.RECEIVED),
    ANSWERED(ProductInquiryStatus.ANSWERED),
}
