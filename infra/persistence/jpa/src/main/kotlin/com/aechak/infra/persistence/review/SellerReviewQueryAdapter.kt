package com.aechak.infra.persistence.review

import com.aechak.application.review.port.view.RatingBucketView
import com.aechak.application.review.seller.port.SellerReviewListCondition
import com.aechak.application.review.seller.port.SellerReviewQueryPort
import com.aechak.application.review.seller.port.view.SellerReviewView
import com.aechak.domain.product.product.QProduct
import com.aechak.domain.review.reply.QReviewReply
import com.aechak.domain.review.review.QReview
import com.aechak.domain.review.review.enums.ReviewStatus
import com.querydsl.core.types.Expression
import com.querydsl.core.types.Predicate
import com.querydsl.core.types.Projections
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Repository

private val review = QReview.review
private val product = QProduct.product
private val reply = QReviewReply.reviewReply

private val VISIBLE_STATUSES: List<ReviewStatus> = ReviewStatus.entries.filter { it.isVisible() }

/**
 * 셀러 시점 리뷰 조회. 소유권은 products.seller_id로만 판정하므로 남의 상품 리뷰는 조건에서 빠져
 * 애초에 결과에 나타나지 않는다 — 권한 오류가 아니라 "없는 것"이 된다.
 */
@Repository
class SellerReviewQueryAdapter(
    private val queryFactory: JPAQueryFactory,
) : SellerReviewQueryPort {
    override fun findPage(condition: SellerReviewListCondition): List<SellerReviewView> =
        queryFactory
            .select(sellerReviewProjection())
            .from(review)
            .join(product)
            .on(product.id.eq(review.productId))
            .leftJoin(reply)
            .on(reply.reviewId.eq(review.id))
            .where(
                product.sellerId.eq(condition.sellerId),
                visible(),
                productPublicIdFilter(condition.productPublicId),
                ratingFilter(condition.rating),
                hasReplyFilter(condition.hasReply),
                keyset(condition.lastReviewId),
            ).orderBy(review.id.desc())
            .limit(condition.limit.toLong())
            .fetch()

    override fun count(condition: SellerReviewListCondition): Long =
        queryFactory
            .select(review.count())
            .from(review)
            .join(product)
            .on(product.id.eq(review.productId))
            .leftJoin(reply)
            .on(reply.reviewId.eq(review.id))
            .where(
                product.sellerId.eq(condition.sellerId),
                visible(),
                productPublicIdFilter(condition.productPublicId),
                ratingFilter(condition.rating),
                hasReplyFilter(condition.hasReply),
            ).fetchOne() ?: 0L

    /**
     * 별점별 건수를 한 번의 group by로 센다. 별점 5개가 각각 COUNT 쿼리를 도는 모양을 피하고,
     * 호출부가 이 결과의 합을 총건수로 재활용해 같은 행을 두 번 세지 않는다.
     */
    override fun findRatingBuckets(
        sellerId: Long,
        productPublicId: String?,
    ): List<RatingBucketView> =
        queryFactory
            .select(Projections.constructor(RatingBucketView::class.java, review.rating, review.count()))
            .from(review)
            .join(product)
            .on(product.id.eq(review.productId))
            .where(product.sellerId.eq(sellerId), visible(), productPublicIdFilter(productPublicId))
            .groupBy(review.rating)
            .fetch()

    override fun existsOwnedVisibleReview(
        reviewId: Long,
        sellerId: Long,
    ): Boolean =
        queryFactory
            .selectOne()
            .from(review)
            .join(product)
            .on(product.id.eq(review.productId))
            .where(review.id.eq(reviewId), product.sellerId.eq(sellerId), visible())
            .fetchFirst() != null

    private fun visible(): Predicate = review.reviewStatus.`in`(VISIBLE_STATUSES)

    private fun productPublicIdFilter(productPublicId: String?): Predicate? = productPublicId?.let { product.publicId.eq(it) }

    private fun ratingFilter(rating: Int?): Predicate? = rating?.let { review.rating.eq(it) }

    private fun hasReplyFilter(hasReply: Boolean?): Predicate? =
        when (hasReply) {
            true -> reply.id.isNotNull
            false -> reply.id.isNull
            null -> null
        }

    private fun keyset(lastReviewId: Long?): Predicate? = lastReviewId?.let { review.id.lt(it) }

    private fun sellerReviewProjection(): Expression<SellerReviewView> =
        Projections.constructor(
            SellerReviewView::class.java,
            review.id,
            product.publicId,
            product.name,
            review.rating,
            review.content,
            review.displayContent,
            review.reviewStatus,
            review.optionNameSnapshot,
            review.authorUserId,
            review.createdAt,
            reply.id,
            reply.content,
            reply.createdAt,
            reply.updatedAt,
        )
}
