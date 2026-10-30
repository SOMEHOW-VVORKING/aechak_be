package com.aechak.application.review.seller.service

import com.aechak.application.review.port.ReviewListQueryPort
import com.aechak.application.review.port.view.RatingBucketView
import com.aechak.application.review.port.view.ReviewImageView
import com.aechak.application.review.seller.port.SellerReviewListCondition
import com.aechak.application.review.seller.port.SellerReviewQueryPort
import com.aechak.application.review.seller.port.view.SellerReviewView
import com.aechak.application.review.seller.usecase.query.SellerReviewListQuery
import com.aechak.application.review.support.ReviewCursorCodec
import com.aechak.application.support.CursorPageResult
import com.aechak.application.support.CursorPageSize
import org.springframework.stereotype.Service

@Service
class SellerReviewQueryService(
    private val sellerReviewQueryPort: SellerReviewQueryPort,
    private val reviewListQueryPort: ReviewListQueryPort,
) {
    /**
     * totalCount는 호출자가 계산해 넘긴다 — 별점·답글 필터가 없을 때는 분포 집계의 합이 곧 총건수라
     * 같은 행을 두 번 세지 않기 위함이다.
     */
    fun getPage(
        query: SellerReviewListQuery,
        totalCount: Long?,
    ): CursorPageResult<SellerReviewView> {
        val anchor =
            query.cursor?.let {
                ReviewCursorCodec.SellerReviews.decode(
                    encodedCursor = it,
                    expectedSellerId = query.sellerId,
                    expectedProductPublicId = query.productPublicId,
                    expectedRating = query.rating,
                    expectedHasReply = query.hasReply,
                )
            }
        val fetched = sellerReviewQueryPort.findPage(conditionOf(query, anchor?.lastReviewId))
        return CursorPageResult.of(fetched = fetched, size = query.size, totalCount = totalCount) { last ->
            ReviewCursorCodec.SellerReviews.encode(
                sellerId = query.sellerId,
                productPublicId = query.productPublicId,
                rating = query.rating,
                hasReply = query.hasReply,
                lastReviewId = last.id,
            )
        }
    }

    fun countFiltered(query: SellerReviewListQuery): Long = sellerReviewQueryPort.count(conditionOf(query, lastReviewId = null))

    fun getImagesByReviewIds(reviewIds: Collection<Long>): List<ReviewImageView> = reviewListQueryPort.findImagesByReviewIds(reviewIds)

    fun getRatingBuckets(query: SellerReviewListQuery): List<RatingBucketView> =
        sellerReviewQueryPort.findRatingBuckets(query.sellerId, query.productPublicId)

    private fun conditionOf(
        query: SellerReviewListQuery,
        lastReviewId: Long?,
    ): SellerReviewListCondition =
        SellerReviewListCondition(
            sellerId = query.sellerId,
            productPublicId = query.productPublicId,
            rating = query.rating,
            hasReply = query.hasReply,
            lastReviewId = lastReviewId,
            limit = CursorPageSize.fetchLimit(query.size),
        )
}
