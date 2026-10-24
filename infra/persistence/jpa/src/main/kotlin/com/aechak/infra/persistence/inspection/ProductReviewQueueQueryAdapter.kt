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
        pageQuery: PageQuery,
    ): List<ProductReviewQueueRowView> =
        baseQuery(
            status,
            Projections.constructor(
                ProductReviewQueueRowView::class.java,
                product.publicId,
                product.name,
                seller.storeName,
                item.status,
                latestJudgment(),
                item.blindedAt,
            ),
        ).orderBy(item.blindedAt.desc(), item.id.desc())
            .offset(pageQuery.page.toLong() * pageQuery.size)
            .limit(pageQuery.size.toLong())
            .fetch()

    override fun count(status: ReviewQueueStatus?): Long = baseQuery(status, item.count()).fetchOne() ?: 0L

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

    private fun <T> baseQuery(
        status: ReviewQueueStatus?,
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
