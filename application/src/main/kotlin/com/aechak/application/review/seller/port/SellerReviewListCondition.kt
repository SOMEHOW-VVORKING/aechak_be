package com.aechak.application.review.seller.port

/**
 * 셀러 시점 리뷰 목록 조건. 상품 필터는 공개 식별자(publicId)로 받고 소유권과 함께 SQL에서 해석한다 —
 * 남의 상품 publicId를 넣어도 조건을 만족하는 행이 없어 "없는 것"으로 응답된다.
 */
data class SellerReviewListCondition(
    val sellerId: Long,
    val productPublicId: String?,
    val rating: Int?,
    val hasReply: Boolean?,
    val lastReviewId: Long?,
    val limit: Int,
) {
    init {
        require(limit > 0) { "limit은 양수여야 합니다." }
        rating?.let { require(it in 1..5) { "rating은 1~5 사이여야 합니다." } }
    }
}
