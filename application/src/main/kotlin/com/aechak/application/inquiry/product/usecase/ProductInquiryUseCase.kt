package com.aechak.application.inquiry.product.usecase

import com.aechak.application.inquiry.product.usecase.command.AskProductInquiryCommand
import com.aechak.application.inquiry.product.usecase.command.WithdrawProductInquiryCommand
import com.aechak.application.inquiry.product.usecase.query.MyProductInquiryListQuery
import com.aechak.application.inquiry.product.usecase.query.ProductInquiryListQuery
import com.aechak.application.inquiry.product.usecase.result.AskProductInquiryResult
import com.aechak.application.inquiry.product.usecase.result.MyProductInquiryItemResult
import com.aechak.application.inquiry.product.usecase.result.ProductInquiryItemResult
import com.aechak.application.support.OffsetPageResult

/** 구매자 쪽 상품 문의 진입점. 셀러센터가 쓰는 메서드와 겹치지 않아 계약을 나눠 둔다. */
interface ProductInquiryUseCase {
    fun askProductInquiry(command: AskProductInquiryCommand): AskProductInquiryResult

    fun withdrawProductInquiry(command: WithdrawProductInquiryCommand)

    /** 상품 상세의 문의 목록. 조회자에 따라 남의 비밀글 본문이 가려진다. */
    fun getProductInquiries(
        query: ProductInquiryListQuery,
        viewerId: Long,
    ): OffsetPageResult<ProductInquiryItemResult>

    fun getMyProductInquiries(query: MyProductInquiryListQuery): OffsetPageResult<MyProductInquiryItemResult>
}
