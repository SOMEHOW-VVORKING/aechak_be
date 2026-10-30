package com.aechak.application.review.seller.port

import com.aechak.application.review.port.view.RatingBucketView
import com.aechak.application.review.seller.port.view.SellerReviewView

/** 셀러가 자기 상품에 달린 리뷰를 보는 조회 포트. 노출 상태(PUBLIC·MASKED) 리뷰만 다룬다. */
interface SellerReviewQueryPort {
    fun findPage(condition: SellerReviewListCondition): List<SellerReviewView>

    fun count(condition: SellerReviewListCondition): Long

    /**
     * 별점별 건수. 별점·답글 필터는 적용하지 않는다 — 분포 막대는 선택한 상품 범위 전체의 모양이어야 한다.
     */
    fun findRatingBuckets(
        sellerId: Long,
        productPublicId: String?,
    ): List<RatingBucketView>

    /** 답글을 달 자격 판정 — 노출 상태이고 이 셀러의 상품에 달린 리뷰일 때만 true. */
    fun existsOwnedVisibleReview(
        reviewId: Long,
        sellerId: Long,
    ): Boolean
}
