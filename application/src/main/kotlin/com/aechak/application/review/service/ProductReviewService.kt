package com.aechak.application.review.service

import com.aechak.application.review.port.ReviewListCondition
import com.aechak.application.review.port.ReviewListQueryPort
import com.aechak.application.review.port.ReviewListSort
import com.aechak.application.review.port.view.RatingBucketView
import com.aechak.application.review.port.view.ReviewImageView
import com.aechak.application.review.port.view.ReviewView
import com.aechak.application.review.support.ReviewCursorCodec
import com.aechak.application.review.usecase.query.ProductReviewListQuery
import com.aechak.application.support.CursorPageResult
import com.aechak.common.error.BusinessException
import com.aechak.common.error.CommonErrorCode
import org.springframework.stereotype.Service

@Service
class ProductReviewService(
    private val reviewListQueryPort: ReviewListQueryPort,
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
                    lastId = anchor?.lastId,
                    lastRating = anchor?.lastRating,
                    limit = query.size + 1,
                ),
            )
        val hasNext = fetched.size > query.size
        val page = if (hasNext) fetched.take(query.size) else fetched
        return CursorPageResult(
            items = page,
            totalCount = if (query.cursor == null) reviewListQueryPort.countVisible(productId, query.photoOnly) else null,
            nextCursor =
                if (hasNext) {
                    val last = page.last()
                    ReviewCursorCodec.encode(query.sort, productId, query.photoOnly, last.id, last.rating)
                } else {
                    null
                },
            hasNext = hasNext,
        )
    }

    fun getRatingBuckets(productId: Long): List<RatingBucketView> = reviewListQueryPort.findRatingBuckets(productId)

    fun getImagesByReviewIds(reviewIds: Collection<Long>): List<ReviewImageView> = reviewListQueryPort.findImagesByReviewIds(reviewIds)

    private fun resolveCursor(
        raw: String,
        productId: Long,
        sort: ReviewListSort,
        photoOnly: Boolean,
    ): CursorAnchor {
        val decoded = ReviewCursorCodec.decode(raw, sort)
        if (decoded.productId != productId || decoded.photoOnly != photoOnly) {
            throw BusinessException(CommonErrorCode.INVALID_CURSOR)
        }
        return CursorAnchor(lastId = decoded.lastId, lastRating = decoded.lastRating)
    }

    private data class CursorAnchor(
        val lastId: Long,
        val lastRating: Int?,
    )
}
