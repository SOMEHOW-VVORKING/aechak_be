package com.aechak.application.inquiry.product.usecase

import com.aechak.application.inquiry.product.usecase.command.AnswerProductInquiryCommand
import com.aechak.application.inquiry.product.usecase.query.SellerProductInquirySearchQuery
import com.aechak.application.inquiry.product.usecase.result.AnswerProductInquiryResult
import com.aechak.application.inquiry.product.usecase.result.SellerProductInquiryPageResult

/** 셀러센터 쪽 상품 문의 진입점. */
interface SellerProductInquiryUseCase {
    /** 내 상품에 온 문의 목록 + 지표. 소유권은 접수 시점에 복사한 sellerId로 잠근다. */
    fun searchMyInquiries(query: SellerProductInquirySearchQuery): SellerProductInquiryPageResult

    fun answerProductInquiry(command: AnswerProductInquiryCommand): AnswerProductInquiryResult
}
