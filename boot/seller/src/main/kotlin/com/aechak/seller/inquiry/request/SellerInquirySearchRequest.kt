package com.aechak.seller.inquiry.request

import com.aechak.application.inquiry.product.port.SellerProductInquiryStatusFilter
import com.aechak.application.inquiry.product.usecase.query.ProductInquiryPageSize
import com.aechak.application.inquiry.product.usecase.query.SellerProductInquirySearchQuery
import com.aechak.domain.inquiry.product.enums.ProductInquiryCategory
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Size
import org.hibernate.validator.constraints.Range

/**
 * 셀러 문의 목록 조회 요청. status를 비우면 답변 대기와 답변 완료를 함께 돌려준다(화면의 "전체" 탭).
 * 철회된 문의는 어느 탭에도 나오지 않으므로 고를 수 있는 값이 아니다.
 */
data class SellerInquirySearchRequest(
    val status: SellerProductInquiryStatusFilter? = null,
    val category: ProductInquiryCategory? = null,
    @field:Size(max = SellerProductInquirySearchQuery.KEYWORD_MAX, message = "검색어는 {max}자를 넘을 수 없습니다.")
    val keyword: String? = null,
    @field:Min(value = 0, message = "page는 0 이상이어야 합니다.")
    val page: Int = 0,
    @field:Range(
        min = ProductInquiryPageSize.MIN,
        max = ProductInquiryPageSize.MAX,
        message = "size는 {min}~{max} 사이여야 합니다.",
    )
    val size: Int = ProductInquiryPageSize.DEFAULT,
) {
    fun toQuery(sellerId: Long): SellerProductInquirySearchQuery =
        SellerProductInquirySearchQuery(
            sellerId = sellerId,
            status = status,
            category = category,
            keyword = keyword?.trim()?.takeIf { it.isNotEmpty() },
            page = page,
            size = size,
        )
}
