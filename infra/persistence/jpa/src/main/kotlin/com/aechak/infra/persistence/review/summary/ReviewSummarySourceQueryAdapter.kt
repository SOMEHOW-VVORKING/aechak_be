package com.aechak.infra.persistence.review.summary

import com.aechak.application.review.summary.port.ReviewSummarySourceQueryPort
import com.aechak.application.review.summary.port.view.SummaryAspectView
import com.aechak.application.review.summary.port.view.SummaryReviewContentView
import com.aechak.application.review.summary.port.view.SummaryReviewStateView
import com.aechak.domain.product.category.enums.CategoryStatus
import com.aechak.domain.product.product.enums.InspectionStatus
import com.aechak.domain.product.product.enums.SaleStatus
import com.aechak.domain.review.review.enums.ReviewStatus
import com.aechak.domain.review.summary.ReviewAspectExtraction
import com.aechak.domain.review.summary.enums.AspectPolarity
import com.aechak.domain.review.summary.enums.ReviewExtractionStatus
import com.aechak.domain.seller.seller.enums.SellerStatus
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class ReviewSummarySourceQueryAdapter(
    private val db: JdbcClient,
) : ReviewSummarySourceQueryPort {
    override fun findRequestTargetProductIds(
        minReviewCount: Int,
        now: LocalDateTime,
        limit: Int,
    ): List<Long> =
        db
            .sql(
                """
                SELECT r.product_id
                FROM reviews r
                JOIN products p ON p.id = r.product_id
                JOIN sellers sl ON sl.user_id = p.seller_id AND sl.status = :activeSeller
                JOIN categories c1 ON c1.id = p.category_id AND c1.status = :activeCategory
                LEFT JOIN categories c2 ON c2.id = c1.parent_id
                LEFT JOIN categories c3 ON c3.id = c2.parent_id
                LEFT JOIN review_aspect_extractions e ON e.review_id = r.id
                LEFT JOIN review_summary_requests s ON s.product_id = r.product_id
                WHERE r.review_status IN (:visibleStatuses)
                  -- 상품 노출 조건은 visibleProductQuery와 같아야 함. 화면에 안 나오는 상품에 모델 비용을 쓰지 않기 위함
                  AND p.inspection_status = :approved AND p.sale_status IN (:exposableSaleStatuses)
                  AND (c2.id IS NULL OR c2.status = :activeCategory)
                  AND (c3.id IS NULL OR c3.status = :activeCategory)
                  AND (s.retry_after IS NULL OR s.retry_after <= :now)
                GROUP BY r.product_id, s.synced_signature, s.requested_at
                HAVING COUNT(*) >= :minReviewCount
                   AND (s.synced_signature IS NULL -- 지문 모양은 ReviewSummaryRequestPlanner.signatureOf와 같아야 함
                        OR s.synced_signature <> CONCAT(COUNT(*), ':', SUM(r.id), ':', BIT_XOR(r.id), ':',
                                                        SUM(CASE WHEN $SETTLED THEN 1 ELSE 0 END)))
                ORDER BY s.requested_at IS NOT NULL, s.requested_at, r.product_id
                LIMIT :limit
                """,
            ).param("exposableSaleStatuses", SaleStatus.EXPOSABLE.map { it.name })
            .param("approved", InspectionStatus.APPROVED.name)
            .param("activeSeller", SellerStatus.ACTIVE.name)
            .param("activeCategory", CategoryStatus.ACTIVE.name)
            .param("visibleStatuses", VISIBLE_STATUSES)
            .param("now", now)
            .param("minReviewCount", minReviewCount)
            .param("extracted", ReviewExtractionStatus.EXTRACTED.name)
            .param("maxFailedAttempts", ReviewAspectExtraction.MAX_FAILED_ATTEMPTS)
            .param("limit", limit)
            .query { rs, _ -> rs.getLong("product_id") }
            .list()

    override fun findVisibleReviewStates(productId: Long): List<SummaryReviewStateView> =
        db
            .sql(
                """
                SELECT r.id, r.rating, r.pet_segment_key,
                       COALESCE(e.status = :extracted, FALSE) AS extracted,
                       COALESCE($SETTLED, FALSE) AS settled
                FROM reviews r
                LEFT JOIN review_aspect_extractions e ON e.review_id = r.id
                WHERE r.product_id = :productId AND r.review_status IN (:visibleStatuses)
                """,
            ).param("productId", productId)
            .param("visibleStatuses", VISIBLE_STATUSES)
            .param("extracted", ReviewExtractionStatus.EXTRACTED.name)
            .param("maxFailedAttempts", ReviewAspectExtraction.MAX_FAILED_ATTEMPTS)
            .query { rs, _ ->
                SummaryReviewStateView(
                    reviewId = rs.getLong("id"),
                    rating = rs.getInt("rating"),
                    segmentKey = rs.getString("pet_segment_key"),
                    extracted = rs.getBoolean("extracted"),
                    settled = rs.getBoolean("settled"),
                )
            }.list()

    override fun findReviewContents(reviewIds: Collection<Long>): List<SummaryReviewContentView> {
        if (reviewIds.isEmpty()) return emptyList()
        return db
            .sql(
                """
                SELECT r.id, r.rating, r.review_status, r.content, r.display_content, r.option_name_snapshot, r.created_at,
                       EXISTS (SELECT 1 FROM review_images i WHERE i.review_id = r.id AND i.deleted_at IS NULL) AS has_photo
                FROM reviews r
                WHERE r.id IN (:reviewIds)
                """,
            ).param("reviewIds", reviewIds)
            .query { rs, _ ->
                SummaryReviewContentView(
                    reviewId = rs.getLong("id"),
                    rating = rs.getInt("rating"),
                    reviewStatus = ReviewStatus.valueOf(rs.getString("review_status")),
                    content = rs.getString("content"),
                    displayContent = rs.getString("display_content"),
                    optionName = rs.getString("option_name_snapshot"),
                    hasPhoto = rs.getBoolean("has_photo"),
                    createdAt = rs.getObject("created_at", LocalDateTime::class.java),
                )
            }.list()
    }

    override fun findExtractedAspectsOfVisibleReviews(productId: Long): List<SummaryAspectView> =
        db
            .sql(
                """
                SELECT a.review_id, r.pet_segment_key, a.aspect_code, a.polarity, a.label, a.quote
                FROM review_aspect_extractions e
                JOIN reviews r ON r.id = e.review_id
                JOIN review_aspects a ON a.review_id = e.review_id
                WHERE e.product_id = :productId AND e.status = :extracted AND r.review_status IN (:visibleStatuses)
                """,
            ).param("productId", productId)
            .param("extracted", ReviewExtractionStatus.EXTRACTED.name)
            .param("visibleStatuses", VISIBLE_STATUSES)
            .query { rs, _ ->
                SummaryAspectView(
                    reviewId = rs.getLong("review_id"),
                    segmentKey = rs.getString("pet_segment_key"),
                    aspectCode = rs.getString("aspect_code"),
                    polarity = AspectPolarity.valueOf(rs.getString("polarity")),
                    label = rs.getString("label"),
                    quote = rs.getString("quote"),
                )
            }.list()

    override fun findCategoryPath(productId: Long): List<String> =
        db
            .sql(
                """
                SELECT c1.name AS root_name, c2.name AS middle_name, c3.name AS leaf_name
                FROM products p
                JOIN categories c3 ON c3.id = p.category_id
                LEFT JOIN categories c2 ON c2.id = c3.parent_id
                LEFT JOIN categories c1 ON c1.id = c2.parent_id
                WHERE p.id = :productId
                """,
            ).param("productId", productId)
            .query { rs, _ -> listOfNotNull(rs.getString("root_name"), rs.getString("middle_name"), rs.getString("leaf_name")) }
            .optional()
            .orElse(emptyList())

    override fun countVisibleReviews(productId: Long): Int =
        db
            .sql("SELECT COUNT(*) FROM reviews WHERE product_id = :productId AND review_status IN (:visibleStatuses)")
            .param("productId", productId)
            .param("visibleStatuses", VISIBLE_STATUSES)
            .query(Int::class.java)
            .single()

    override fun findVisibleReviewIdsOfProduct(
        productId: Long,
        reviewIds: Collection<Long>,
    ): Set<Long> {
        if (reviewIds.isEmpty()) return emptySet()
        return db
            .sql("SELECT id FROM reviews WHERE product_id = :productId AND id IN (:reviewIds) AND review_status IN (:visibleStatuses)")
            .param("productId", productId)
            .param("reviewIds", reviewIds.toSet())
            .param("visibleStatuses", VISIBLE_STATUSES)
            .query { rs, _ -> rs.getLong("id") }
            .set()
    }

    override fun findReviewIdsOfProduct(
        productId: Long,
        reviewIds: Collection<Long>,
    ): Set<Long> {
        if (reviewIds.isEmpty()) return emptySet()
        return db
            .sql("SELECT id FROM reviews WHERE product_id = :productId AND id IN (:reviewIds)")
            .param("productId", productId)
            .param("reviewIds", reviewIds.toSet())
            .query { rs, _ -> rs.getLong("id") }
            .set()
    }

    companion object {
        private val VISIBLE_STATUSES = ReviewStatus.entries.filter { it.isVisible() }.map { it.name }

        // ReviewAspectExtraction.isSettled()와 같은 판정. e가 없는 리뷰(아직 보낸 적 없음)는 NULL이라 미확정
        private const val SETTLED =
            "(e.status = :extracted OR e.exclude_reason IS NOT NULL OR e.failed_attempt_count >= :maxFailedAttempts)"
    }
}
