package com.aechak.api.inquiry.request

import com.aechak.application.inquiry.product.usecase.query.MyProductInquiryListQuery
import com.aechak.application.inquiry.product.usecase.query.ProductInquiryPageSize
import com.aechak.domain.inquiry.product.enums.ProductInquiryStatus
import jakarta.validation.constraints.Min
import org.hibernate.validator.constraints.Range

data class MyProductInquiryListRequest(
    /** 비우면 철회한 문의까지 전부 */
    val status: ProductInquiryStatus? = null,
    @field:Min(value = 0, message = "page는 0 이상이어야 합니다.")
    val page: Int = 0,
    @field:Range(
        min = ProductInquiryPageSize.MIN,
        max = ProductInquiryPageSize.MAX,
        message = "size는 {min}~{max} 사이여야 합니다.",
    )
    val size: Int = ProductInquiryPageSize.DEFAULT,
) {
    fun toQuery(authorUserId: Long): MyProductInquiryListQuery =
        MyProductInquiryListQuery(authorUserId = authorUserId, status = status, page = page, size = size)
}
