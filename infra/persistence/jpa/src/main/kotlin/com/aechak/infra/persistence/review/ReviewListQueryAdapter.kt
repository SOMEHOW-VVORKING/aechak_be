package com.aechak.infra.persistence.review

import com.aechak.application.review.port.ReviewListCondition
import com.aechak.application.review.port.ReviewListQueryPort
import com.aechak.application.review.port.ReviewListSort
import com.aechak.application.review.port.view.RatingBucketView
import com.aechak.application.review.port.view.ReviewImageView
import com.aechak.application.review.port.view.ReviewView
import com.aechak.domain.review.review.QReview
import com.aechak.domain.review.review.QReviewImage
import com.aechak.domain.review.review.enums.ReviewStatus
import com.querydsl.core.types.Expression
import com.querydsl.core.types.OrderSpecifier
import com.querydsl.core.types.Predicate
import com.querydsl.core.types.Projections
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Repository

private val review = QReview.review
private val reviewImage = QReviewImage.reviewImage

private val VISIBLE_STATUSES: List<ReviewStatus> = ReviewStatus.entries.filter { it.isVisible() }

@Repository
class ReviewListQueryAdapter(
    private val queryFactory: JPAQueryFactory,
) : ReviewListQueryPort {
    override fun findVisiblePage(condition: ReviewListCondition): List<ReviewView> =
        queryFactory
            .select(reviewProjection())
            .from(review)
            .where(
                review.productId.eq(condition.productId),
                visible(),
                photoOnlyFilter(condition.photoOnly),
                keyset(condition),
            ).orderBy(*orderBy(condition.sort))
            .limit(condition.limit.toLong())
            .fetch()

    override fun countVisible(
        productId: Long,
        photoOnly: Boolean,
    ): Long =
        queryFactory
            .select(review.count())
            .from(review)
            .where(review.productId.eq(productId), visible(), photoOnlyFilter(photoOnly))
            .fetchOne() ?: 0L

    override fun findImagesByReviewIds(reviewIds: Collection<Long>): List<ReviewImageView> {
        if (reviewIds.isEmpty()) return emptyList()
        return queryFactory
            .select(
                Projections.constructor(
                    ReviewImageView::class.java,
                    review.id,
                    reviewImage.storageKey,
                    reviewImage.sortOrder,
                ),
            ).from(review)
            .join(review._images, reviewImage)
            .where(review.id.`in`(reviewIds), reviewImage.deletedAt.isNull)
            .orderBy(review.id.asc(), reviewImage.sortOrder.asc(), reviewImage.id.asc())
            .fetch()
    }

    override fun findRatingBuckets(productId: Long): List<RatingBucketView> =
        queryFactory
            .select(Projections.constructor(RatingBucketView::class.java, review.rating, review.count()))
            .from(review)
            .where(review.productId.eq(productId), visible())
            .groupBy(review.rating)
            .fetch()

    private fun visible(): Predicate = review.reviewStatus.`in`(VISIBLE_STATUSES)

    private fun photoOnlyFilter(photoOnly: Boolean): Predicate? =
        if (photoOnly) {
            review._images
                .any()
                .deletedAt.isNull
        } else {
            null
        }

    private fun keyset(condition: ReviewListCondition): Predicate? {
        val lastReviewId = condition.lastReviewId ?: return null
        return when (condition.sort) {
            ReviewListSort.LATEST -> {
                review.id.lt(lastReviewId)
            }

            ReviewListSort.RATING_DESC -> {
                val lastRating = requireNotNull(condition.lastRating)
                review.rating
                    .lt(lastRating)
                    .or(review.rating.eq(lastRating).and(review.id.lt(lastReviewId)))
            }
        }
    }

    private fun orderBy(sort: ReviewListSort): Array<OrderSpecifier<*>> =
        when (sort) {
            ReviewListSort.LATEST -> arrayOf(review.id.desc())
            ReviewListSort.RATING_DESC -> arrayOf(review.rating.desc(), review.id.desc())
        }

    private fun reviewProjection(): Expression<ReviewView> =
        Projections.constructor(
            ReviewView::class.java,
            review.id,
            review.rating,
            review.content,
            review.displayContent,
            review.reviewStatus,
            review.optionNameSnapshot,
            review.authorUserId,
            review.createdAt,
        )
}
