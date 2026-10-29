package com.aechak.infra.persistence.review

import com.aechak.application.review.admin.port.AdminReviewListCondition
import com.aechak.application.review.admin.port.AdminReviewQueryPort
import com.aechak.application.review.admin.port.view.AdminReviewCountView
import com.aechak.application.review.admin.port.view.AdminReviewReportView
import com.aechak.application.review.admin.port.view.AdminReviewView
import com.aechak.domain.product.product.QProduct
import com.aechak.domain.review.report.QReviewReport
import com.aechak.domain.review.review.QReview
import com.aechak.domain.review.review.QReviewImage
import com.aechak.domain.review.review.enums.ReviewStatus
import com.aechak.domain.seller.seller.QSeller
import com.querydsl.core.types.Expression
import com.querydsl.core.types.Predicate
import com.querydsl.core.types.Projections
import com.querydsl.jpa.JPAExpressions
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Repository

private val review = QReview.review
private val reviewImage = QReviewImage.reviewImage
private val reviewReport = QReviewReport.reviewReport
private val product = QProduct.product
private val seller = QSeller.seller

/**
 * 운영자 시점 리뷰 조회. 구매자 조회와 달리 상태 필터를 걸지 않아 블라인드·삭제된 리뷰까지 보인다 —
 * 가려진 것을 되돌릴 수 있어야 하는 쪽이라 노출 상태가 조건이 아니라 결과의 한 축이다.
 *
 * 상품·셀러 조인으로 이름을 바로 읽는다(셀러 리뷰 조회와 같은 결). 상품은 리뷰가 값으로 참조하므로
 * id 동등 조건으로 잇고, 셀러 행이 없는 상품도 목록에서 사라지지 않게 leftJoin으로 둔다.
 */
@Repository
class AdminReviewQueryAdapter(
    private val queryFactory: JPAQueryFactory,
) : AdminReviewQueryPort {
    override fun findPage(condition: AdminReviewListCondition): List<AdminReviewView> =
        queryFactory
            .select(adminReviewProjection())
            .from(review)
            .join(product)
            .on(product.id.eq(review.productId))
            .leftJoin(seller)
            .on(seller.userId.eq(product.sellerId))
            .where(*filters(condition), keyset(condition.lastReviewId))
            .orderBy(review.id.desc())
            .limit(condition.limit.toLong())
            .fetch()

    override fun count(condition: AdminReviewListCondition): Long =
        queryFactory
            .select(review.count())
            .from(review)
            .join(product)
            .on(product.id.eq(review.productId))
            .where(*filters(condition))
            .fetchOne() ?: 0L

    override fun findById(reviewId: Long): AdminReviewView? =
        queryFactory
            .select(adminReviewProjection())
            .from(review)
            .join(product)
            .on(product.id.eq(review.productId))
            .leftJoin(seller)
            .on(seller.userId.eq(product.sellerId))
            .where(review.id.eq(reviewId))
            .fetchOne()

    override fun countPhotosByReviewIds(reviewIds: Collection<Long>): List<AdminReviewCountView> {
        if (reviewIds.isEmpty()) return emptyList()
        return queryFactory
            .select(Projections.constructor(AdminReviewCountView::class.java, review.id, reviewImage.count()))
            .from(review)
            .join(review._images, reviewImage)
            .where(review.id.`in`(reviewIds), reviewImage.deletedAt.isNull)
            .groupBy(review.id)
            .fetch()
    }

    override fun countReportsByReviewIds(reviewIds: Collection<Long>): List<AdminReviewCountView> {
        if (reviewIds.isEmpty()) return emptyList()
        return queryFactory
            .select(Projections.constructor(AdminReviewCountView::class.java, reviewReport.review.id, reviewReport.count()))
            .from(reviewReport)
            .where(reviewReport.review.id.`in`(reviewIds))
            .groupBy(reviewReport.review.id)
            .fetch()
    }

    override fun findReportsByReviewId(reviewId: Long): List<AdminReviewReportView> =
        queryFactory
            .select(
                Projections.constructor(
                    AdminReviewReportView::class.java,
                    reviewReport.id,
                    reviewReport.reporterId,
                    reviewReport.reasonCode,
                    reviewReport.reasonText,
                    reviewReport.status,
                    reviewReport.createdAt,
                ),
            ).from(reviewReport)
            .where(reviewReport.review.id.eq(reviewId))
            .orderBy(reviewReport.id.desc())
            .fetch()

    private fun filters(condition: AdminReviewListCondition): Array<Predicate?> =
        arrayOf(
            productFilter(condition.productPublicId),
            ratingFilter(condition.rating),
            statusFilter(condition.status),
            reportedFilter(condition.reportedOnly),
            keywordFilter(condition.keyword),
        )

    private fun productFilter(productPublicId: String?): Predicate? = productPublicId?.let { product.publicId.eq(it) }

    private fun ratingFilter(rating: Int?): Predicate? = rating?.let { review.rating.eq(it) }

    private fun statusFilter(status: ReviewStatus?): Predicate? = status?.let { review.reviewStatus.eq(it) }

    private fun reportedFilter(reportedOnly: Boolean): Predicate? =
        if (reportedOnly) {
            JPAExpressions
                .selectOne()
                .from(reviewReport)
                .where(reviewReport.review.id.eq(review.id))
                .exists()
        } else {
            null
        }

    /** 리뷰 본문과 상품명 양쪽을 본다 — 운영자는 어느 쪽 단서로든 같은 행을 찾는다. */
    private fun keywordFilter(keyword: String?): Predicate? = keyword?.let { review.content.contains(it).or(product.name.contains(it)) }

    private fun keyset(lastReviewId: Long?): Predicate? = lastReviewId?.let { review.id.lt(it) }

    private fun adminReviewProjection(): Expression<AdminReviewView> =
        Projections.constructor(
            AdminReviewView::class.java,
            review.id,
            review.productId,
            product.publicId,
            product.name,
            seller.storeName,
            review.authorUserId,
            review.rating,
            review.content,
            review.displayContent,
            review.reviewStatus,
            review.optionNameSnapshot,
            review.blindActor,
            review.blindReasonCode,
            review.blindReasonText,
            review.blindedAt,
            review.blindedByAdminId,
            review.deletedByAdminId,
            review.deletedAt,
            review.createdAt,
        )
}
