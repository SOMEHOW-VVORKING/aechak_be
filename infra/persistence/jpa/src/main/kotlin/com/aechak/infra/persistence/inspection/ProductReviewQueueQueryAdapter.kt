package com.aechak.infra.persistence.inspection

import com.aechak.application.inspection.port.ProductReviewQueueQueryPort
import com.aechak.application.inspection.port.view.ProductReviewQueueRowView
import com.aechak.application.inspection.port.view.ReviewTargetProductView
import com.aechak.application.support.PageQuery
import com.aechak.domain.inspection.QContentInspection
import com.aechak.domain.inspection.QProductReviewQueueItem
import com.aechak.domain.inspection.enums.InspectionTargetType
import com.aechak.domain.inspection.enums.ReviewQueueStatus
import com.aechak.domain.product.category.QCategory
import com.aechak.domain.product.option.repository.OptionGroupRepository
import com.aechak.domain.product.product.QProduct
import com.aechak.domain.seller.seller.QSeller
import com.querydsl.core.types.Expression
import com.querydsl.core.types.OrderSpecifier
import com.querydsl.core.types.Projections
import com.querydsl.jpa.JPAExpressions
import com.querydsl.jpa.impl.JPAQuery
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Repository

private val item = QProductReviewQueueItem.productReviewQueueItem
private val product = QProduct.product
private val seller = QSeller.seller
private val latest = QContentInspection("latest")
private val newer = QContentInspection("newer")
private val leaf = QCategory("leaf")
private val mid = QCategory("mid")
private val root = QCategory("root")

@Repository
class ProductReviewQueueQueryAdapter(
    private val queryFactory: JPAQueryFactory,
    private val optionGroupRepository: OptionGroupRepository,
) : ProductReviewQueueQueryPort {
    override fun findPage(
        status: ReviewQueueStatus?,
        appealedOnly: Boolean,
        pageQuery: PageQuery,
    ): List<ProductReviewQueueRowView> =
        baseQuery(
            status,
            appealedOnly,
            Projections.constructor(
                ProductReviewQueueRowView::class.java,
                product.publicId,
                product.name,
                seller.storeName,
                item.status,
                latestJudgment(),
                item.blindedAt,
                item.appealedAt,
            ),
        ).orderBy(*order(appealedOnly))
            .offset(pageQuery.page.toLong() * pageQuery.size)
            .limit(pageQuery.size.toLong())
            .fetch()

    /**
     * 정렬 축을 필터 축에 맞춘다.
     *
     * 전체 목록의 축은 `blinded_at`(행을 열었거나 다시 연 판정의 시각)이다. 그런데 이의로 새로
     * 연 행은 그 칼럼을 **원 판정의 시각**으로 찍으므로, 이의 목록을 그 축으로 정렬하면 3주 전에
     * 반려된 상품에 오늘 걸린 이의가 맨 아래로 간다 — 위에서부터 처리하는 관리자는 가장 묵은
     * 이의부터 보고 새 이의는 목록이 길면 영구히 못 본다. 그 사이 그 행은 셀러 정원을 먹는다.
     */
    private fun order(appealedOnly: Boolean): Array<OrderSpecifier<*>> =
        if (appealedOnly) {
            arrayOf(item.appealedAt.desc(), item.id.desc())
        } else {
            arrayOf(item.blindedAt.desc(), item.id.desc())
        }

    override fun count(
        status: ReviewQueueStatus?,
        appealedOnly: Boolean,
    ): Long = baseQuery(status, appealedOnly, item.count()).fetchOne() ?: 0L

    override fun findReviewTarget(productPublicId: String): ReviewTargetProductView? {
        val row =
            queryFactory
                .select(
                    product.id,
                    product.name,
                    seller.storeName,
                    root.name,
                    mid.name,
                    leaf.name,
                    product.description,
                    product.representativeImageKey,
                    product.inspectionStatus,
                ).from(product)
                .join(seller)
                .on(seller.userId.eq(product.sellerId))
                .join(product.category, leaf)
                .leftJoin(leaf.parent, mid)
                .leftJoin(mid.parent, root)
                .where(product.publicId.eq(productPublicId))
                .fetchOne() ?: return null

        val productId = row.get(product.id)!!
        return ReviewTargetProductView(
            productId = productId,
            productPublicId = productPublicId,
            productName = row.get(product.name)!!,
            sellerName = row.get(seller.storeName)!!,
            categoryNames = listOfNotNull(row.get(root.name), row.get(mid.name), row.get(leaf.name)),
            description = row.get(product.description),
            representativeImageKey = row.get(product.representativeImageKey),
            optionNames =
                optionGroupRepository
                    .findByProductId(productId)
                    .flatMap { group -> listOf(group.name) + group.values.sortedBy { it.sortOrder }.map { it.name } },
            inspectionStatus = row.get(product.inspectionStatus)!!,
        )
    }

    /**
     * 이의 필터에 전용 인덱스를 두지 않는다. status 등호 + appealed_at 널 아님 + blinded_at 정렬인데,
     * (status, appealed_at)을 만들면 필터는 받고 정렬을 잃는다(파일소트). 기존 (status, blinded_at)이
     * 등호와 정렬을 함께 받고 이의는 잔여 필터로 두는 쪽이 지금 행 수에서 이득이다.
     */
    private fun <T> baseQuery(
        status: ReviewQueueStatus?,
        appealedOnly: Boolean,
        select: Expression<T>,
    ): JPAQuery<T> =
        queryFactory
            .select(select)
            .from(item)
            .join(product)
            .on(product.id.eq(item.targetId))
            .join(seller)
            .on(seller.userId.eq(product.sellerId))
            .where(
                item.targetType.eq(InspectionTargetType.PRODUCT),
                status?.let(item.status::eq),
                item.appealedAt.isNotNull.takeIf { appealedOnly },
            )

    /** 상품의 가장 최근 검수 판정 */
    private fun latestJudgment() =
        JPAExpressions
            .select(latest.judgment)
            .from(latest)
            .where(
                latest.id.eq(
                    JPAExpressions
                        .select(newer.id.max())
                        .from(newer)
                        .where(newer.targetType.eq(InspectionTargetType.PRODUCT), newer.targetRef.eq(item.targetId)),
                ),
            )
}
