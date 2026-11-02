package com.aechak.domain.product.report.repository

import com.aechak.domain.product.report.ProductReport

interface ProductReportRepository {
    /** 저장하고 즉시 flush한다. 같은 신고자가 같은 상품을 이미 신고했으면 DuplicateProductReportException을 던진다. */
    fun save(report: ProductReport): ProductReport
}
