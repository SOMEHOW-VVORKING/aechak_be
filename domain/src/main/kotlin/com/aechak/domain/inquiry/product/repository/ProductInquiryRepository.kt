package com.aechak.domain.inquiry.product.repository

import com.aechak.domain.inquiry.product.ProductInquiry

interface ProductInquiryRepository {
    fun save(inquiry: ProductInquiry): ProductInquiry

    /**
     * 상태를 바꾸는 두 경로(셀러 답변·구매자 철회)가 쓰는 조회. 행을 잠가 읽는다 —
     * 상태 가드는 읽은 값으로 판정하므로 잠그지 않으면 답변과 철회가 같은 RECEIVED를 보고
     * 둘 다 성공해 "답변이 달린 WITHDRAWN"이 저장된다. @Version으로는 못 거른다:
     * 자식 INSERT가 루트 UPDATE보다 먼저 flush돼 답변의 UNIQUE(inquiry_id)가 버전 검사보다
     * 먼저 터지고, 그러면 두 번째 답변이 409가 아니라 500이 된다(ProductRepository 선례와 같은 결).
     */
    fun findByPublicIdForUpdate(publicId: String): ProductInquiry?
}
