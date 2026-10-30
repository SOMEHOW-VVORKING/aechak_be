package com.aechak.application.review.seller.usecase.query

import com.aechak.application.support.CursorPageSize

/** 셀러 리뷰 목록 조회 입력. productPublicId·rating·hasReply는 화면 필터 세 개에 1:1로 대응한다. */
data class SellerReviewListQuery(
    val sellerId: Long,
    val productPublicId: String? = null,
    val rating: Int? = null,
    val hasReply: Boolean? = null,
    val cursor: String? = null,
    val size: Int = CursorPageSize.DEFAULT,
) {
    init {
        require(size in CursorPageSize.MIN..CursorPageSize.MAX) { "size는 ${CursorPageSize.MIN}~${CursorPageSize.MAX} 범위 안에 있어야 합니다." }
        rating?.let { require(it in 1..5) { "rating은 1~5 사이여야 합니다." } }
    }
}
