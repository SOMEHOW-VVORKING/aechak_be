package com.aechak.domain.product.report.repository

/** 같은 신고자가 같은 상품을 이미 신고한 경우 발생한다. */
class DuplicateProductReportException(
    cause: Throwable,
) : RuntimeException(cause)
