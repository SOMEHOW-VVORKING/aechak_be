package com.aechak.application.inquiry.product.usecase

import com.aechak.application.inquiry.product.port.view.SellerUnansweredInquiryCountView
import com.aechak.application.inquiry.product.usecase.command.AnswerProductInquiryCommand
import com.aechak.application.inquiry.product.usecase.query.SellerProductInquirySearchQuery
import com.aechak.application.inquiry.product.usecase.query.SellerUnansweredInquiryQuery
import com.aechak.application.inquiry.product.usecase.result.AnswerProductInquiryResult
import com.aechak.application.inquiry.product.usecase.result.SellerProductInquiryPageResult

/** 셀러센터 쪽 상품 문의 진입점. */
interface SellerProductInquiryUseCase {
    /** 내 상품에 온 문의 목록 + 지표. 소유권은 접수 시점에 복사한 sellerId로 잠근다. */
    fun searchMyInquiries(query: SellerProductInquirySearchQuery): SellerProductInquiryPageResult

    fun answerProductInquiry(command: AnswerProductInquiryCommand): AnswerProductInquiryResult

    /**
     * 셀러 대시보드의 미응답 문의 건수. 자격 게이트는 대시보드 파사드가 한 번 보므로 여기서 다시 보지 않는다 —
     * 넘겨받은 sellerId 범위의 건수만 돌려주고 식별자를 내지 않는다.
     */
    fun countUnansweredInquiries(query: SellerUnansweredInquiryQuery): SellerUnansweredInquiryCountView
}
