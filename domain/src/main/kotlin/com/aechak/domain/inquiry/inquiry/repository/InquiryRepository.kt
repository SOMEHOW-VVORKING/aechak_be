package com.aechak.domain.inquiry.inquiry.repository

import com.aechak.domain.inquiry.inquiry.Inquiry

interface InquiryRepository {
    fun save(inquiry: Inquiry): Inquiry

    fun findById(inquiryId: Long): Inquiry?

    /**
     * 답변 등록·상태 전이가 쓰는 조회. 행을 잠가 읽어야 상태 판정과 반영 사이에 다른 운영자의 처리가
     * 끼어들지 못한다 — Inquiry에는 @Version이 없고, 판정에서 막힌 요청은 아무것도 쓰지 않아
     * 낙관적 잠금으로는 애초에 못 거른다.
     */
    fun findByIdForUpdate(inquiryId: Long): Inquiry?
}
