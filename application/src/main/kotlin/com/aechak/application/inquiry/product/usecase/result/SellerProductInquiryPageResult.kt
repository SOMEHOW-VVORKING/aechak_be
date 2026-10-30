package com.aechak.application.inquiry.product.usecase.result

import com.aechak.application.support.OffsetPageResult

/** 셀러 문의 목록 + 지표. 한 번의 호출로 화면이 필요한 전부를 받는다. */
data class SellerProductInquiryPageResult(
    val page: OffsetPageResult<SellerProductInquiryItemResult>,
    val metrics: SellerProductInquiryMetricsResult,
)
