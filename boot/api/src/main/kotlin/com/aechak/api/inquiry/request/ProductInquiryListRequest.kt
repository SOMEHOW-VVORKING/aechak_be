package com.aechak.api.inquiry.request

import com.aechak.application.inquiry.product.usecase.query.ProductInquiryListQuery
import com.aechak.application.inquiry.product.usecase.query.ProductInquiryPageSize
import jakarta.validation.constraints.Min
import org.hibernate.validator.constraints.Range

data class ProductInquiryListRequest(
    @field:Min(value = 0, message = "page는 0 이상이어야 합니다.")
    val page: Int = 0,
    @field:Range(
        min = ProductInquiryPageSize.MIN,
        max = ProductInquiryPageSize.MAX,
        message = "size는 {min}~{max} 사이여야 합니다.",
    )
    val size: Int = ProductInquiryPageSize.DEFAULT,
) {
    fun toQuery(productPublicId: String): ProductInquiryListQuery =
        ProductInquiryListQuery(productPublicId = productPublicId, page = page, size = size)
}
