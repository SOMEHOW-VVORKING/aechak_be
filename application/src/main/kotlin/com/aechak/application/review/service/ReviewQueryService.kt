package com.aechak.application.review.service

import com.aechak.application.review.port.MyReviewQueryPort
import com.aechak.application.review.port.ReviewListCondition
import com.aechak.application.review.port.ReviewListQueryPort
import com.aechak.application.review.port.ReviewListSort
import com.aechak.application.review.port.UnreviewedOrderItemListCondition
import com.aechak.application.review.port.WrittenReviewListCondition
import com.aechak.application.review.port.view.RatingBucketView
import com.aechak.application.review.port.view.ReviewImageView
import com.aechak.application.review.port.view.ReviewView
import com.aechak.application.review.port.view.UnreviewedOrderItemView
import com.aechak.application.review.port.view.WrittenReviewView
import com.aechak.application.review.support.ReviewCursorCodec
import com.aechak.application.review.usecase.query.MyReviewListQuery
import com.aechak.application.review.usecase.query.ProductReviewListQuery
import com.aechak.application.support.CursorPageResult
import com.aechak.application.support.CursorPageSize
import org.springframework.stereotype.Service

@Service
class ReviewQueryService(
    private val reviewListQueryPort: ReviewListQueryPort,
    private val myReviewQueryPort: MyReviewQueryPort,
) {
    fun getVisiblePage(
        productId: Long,
        query: ProductReviewListQuery,
    ): CursorPageResult<ReviewView> {
        val anchor = query.cursor?.let { resolveCursor(it, productId, query.sort, query.photoOnly) }
        val fetched =
            reviewListQueryPort.findVisiblePage(
                ReviewListCondition(
                    productId = productId,
                    sort = query.sort,
                    photoOnly = query.photoOnly,
                    lastReviewId = anchor?.lastReviewId,
                    lastRating = anchor?.lastRating,
                    limit = CursorPageSize.fetchLimit(query.size),
                ),
            )
        return CursorPageResult.of(
            fetched = fetched,
            size = query.size,
            totalCount = if (query.cursor == null) reviewListQueryPort.countVisible(productId, query.photoOnly) else null,
        ) { last ->
            when (query.sort) {
                ReviewListSort.LATEST -> {
                    ReviewCursorCodec.ProductReviews.encodeLatest(
                        productId = productId,
                        photoOnly = query.photoOnly,
                        lastReviewId = last.id,
                    )
                }

                ReviewListSort.RATING_DESC -> {
                    ReviewCursorCodec.ProductReviews.encodeRatingDesc(
                        productId = productId,
                        photoOnly = query.photoOnly,
                        lastRating = last.rating,
                        lastReviewId = last.id,
                    )
                }
            }
        }
    }

    fun getRatingBuckets(productId: Long): List<RatingBucketView> = reviewListQueryPort.findRatingBuckets(productId)

    fun getImagesByReviewIds(reviewIds: Collection<Long>): List<ReviewImageView> = reviewListQueryPort.findImagesByReviewIds(reviewIds)

    fun getWrittenReviewPage(query: MyReviewListQuery): CursorPageResult<WrittenReviewView> {
        val anchor = query.cursor?.let { ReviewCursorCodec.MyReviews.decodeWritten(it, query.userId) }
        val fetched =
            myReviewQueryPort.findWrittenReviewPage(
                WrittenReviewListCondition(
                    authorUserId = query.userId,
                    anchor = anchor,
                    limit = CursorPageSize.fetchLimit(query.size),
                ),
            )
        return CursorPageResult.of(
            fetched = fetched,
            size = query.size,
            totalCount = if (query.cursor == null) myReviewQueryPort.countWrittenReviews(query.userId) else null,
        ) { last -> ReviewCursorCodec.MyReviews.encodeWritten(query.userId, last.reviewId) }
    }

    fun getUnreviewedOrderItemPage(query: MyReviewListQuery): CursorPageResult<UnreviewedOrderItemView> {
        val anchor = query.cursor?.let { ReviewCursorCodec.MyReviews.decodeUnreviewedOrderItem(it, query.userId) }
        val fetched =
            myReviewQueryPort.findUnreviewedOrderItemPage(
                UnreviewedOrderItemListCondition(
                    buyerId = query.userId,
                    anchor = anchor,
                    limit = CursorPageSize.fetchLimit(query.size),
                ),
            )
        return CursorPageResult.of(
            fetched = fetched,
            size = query.size,
            totalCount = if (query.cursor == null) myReviewQueryPort.countUnreviewedOrderItems(query.userId) else null,
        ) { last ->
            ReviewCursorCodec.MyReviews.encodeUnreviewedOrderItem(query.userId, last.purchaseConfirmedAt, last.orderItemId)
        }
    }

    private fun resolveCursor(
        encodedCursor: String,
        productId: Long,
        sort: ReviewListSort,
        photoOnly: Boolean,
    ): CursorAnchor =
        when (sort) {
            ReviewListSort.LATEST -> {
                val decoded = ReviewCursorCodec.ProductReviews.decodeLatest(encodedCursor, productId, photoOnly)
                CursorAnchor(lastReviewId = decoded.lastReviewId, lastRating = null)
            }

            ReviewListSort.RATING_DESC -> {
                val decoded = ReviewCursorCodec.ProductReviews.decodeRatingDesc(encodedCursor, productId, photoOnly)
                CursorAnchor(lastReviewId = decoded.lastReviewId, lastRating = decoded.lastRating)
            }
        }

    private data class CursorAnchor(
        val lastReviewId: Long,
        val lastRating: Int?,
    )
}
