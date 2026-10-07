package com.aechak.infra.persistence.review

import com.aechak.application.review.port.MyReviewQueryPort
import com.aechak.application.review.port.UnreviewedOrderItemAnchor
import com.aechak.application.review.port.UnreviewedOrderItemListCondition
import com.aechak.application.review.port.WrittenReviewAnchor
import com.aechak.application.review.port.WrittenReviewListCondition
import com.aechak.application.review.port.view.UnreviewedOrderItemView
import com.aechak.application.review.port.view.WrittenReviewView
import com.aechak.domain.order.group.QOrderGroup
import com.aechak.domain.order.order.QOrder
import com.aechak.domain.order.order.QOrderItem
import com.aechak.domain.order.order.enums.OrderItemStatus
import com.aechak.domain.order.order.enums.OrderStatus
import com.aechak.domain.product.product.QProduct
import com.aechak.domain.product.version.QProductVersion
import com.aechak.domain.review.review.QReview
import com.aechak.domain.review.review.enums.ReviewStatus
import com.querydsl.core.types.Expression
import com.querydsl.core.types.Predicate
import com.querydsl.core.types.Projections
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

private val review = QReview.review
private val order = QOrder.order
private val orderItem = QOrderItem.orderItem
private val orderGroup = QOrderGroup.orderGroup
private val productVersion = QProductVersion.productVersion
private val product = QProduct.product

@Repository
class MyReviewQueryAdapter(
    private val queryFactory: JPAQueryFactory,
) : MyReviewQueryPort {
    override fun findWrittenReviewPage(condition: WrittenReviewListCondition): List<WrittenReviewView> =
        queryFactory
            .select(writtenReviewProjection())
            .from(review)
            .join(orderItem)
            .on(orderItem.id.eq(review.orderItemId))
            .join(productVersion)
            .on(productVersion.id.eq(orderItem.productVersionId))
            .join(productVersion.product, product)
            .where(
                review.authorUserId.eq(condition.authorUserId),
                notDeleted(),
                writtenReviewKeyset(condition.anchor),
            ).orderBy(review.id.desc())
            .limit(condition.limit.toLong())
            .fetch()

    override fun findUnreviewedOrderItemPage(condition: UnreviewedOrderItemListCondition): List<UnreviewedOrderItemView> =
        queryFactory
            .select(unreviewedOrderItemProjection())
            .from(order)
            .join(order._items, orderItem)
            .join(order.orderGroup, orderGroup)
            .join(productVersion)
            .on(productVersion.id.eq(orderItem.productVersionId))
            .join(productVersion.product, product)
            .leftJoin(review)
            .on(review.orderItemId.eq(orderItem.id))
            .where(
                orderGroup.buyerId.eq(condition.buyerId),
                order.status.eq(OrderStatus.PURCHASE_CONFIRMED),
                orderItem.itemStatus.eq(OrderItemStatus.ORDERED),
                // 삭제된 리뷰도 재작성할 수 없으므로 리뷰 행이 없는 주문 품목만 조회
                review.id.isNull,
                unreviewedOrderItemKeyset(condition.anchor),
            ).orderBy(order.purchaseConfirmedAt.desc(), orderItem.id.desc())
            .limit(condition.limit.toLong())
            .fetch()

    private fun notDeleted(): Predicate = review.reviewStatus.ne(ReviewStatus.DELETED)

    private fun writtenReviewKeyset(anchor: WrittenReviewAnchor?): Predicate? = anchor?.let { review.id.lt(it.lastReviewId) }

    override fun countWrittenReviews(authorUserId: Long): Long =
        queryFactory
            .select(review.count())
            .from(review)
            .where(review.authorUserId.eq(authorUserId), notDeleted())
            .fetchOne() ?: 0L

    private fun unreviewedOrderItemKeyset(anchor: UnreviewedOrderItemAnchor?): Predicate? {
        if (anchor == null) return null
        return order.purchaseConfirmedAt
            .lt(anchor.lastConfirmedAt)
            .or(order.purchaseConfirmedAt.eq(anchor.lastConfirmedAt).and(orderItem.id.lt(anchor.lastOrderItemId)))
    }

    override fun countUnreviewedOrderItems(buyerId: Long): Long =
        queryFactory
            .select(orderItem.count())
            .from(order)
            .join(order._items, orderItem)
            .join(order.orderGroup, orderGroup)
            .leftJoin(review)
            .on(review.orderItemId.eq(orderItem.id))
            .where(
                orderGroup.buyerId.eq(buyerId),
                order.status.eq(OrderStatus.PURCHASE_CONFIRMED),
                orderItem.itemStatus.eq(OrderItemStatus.ORDERED),
                review.id.isNull,
            ).fetchOne() ?: 0L

    private fun writtenReviewProjection(): Expression<WrittenReviewView> =
        Projections.constructor(
            WrittenReviewView::class.java,
            review.id,
            review.rating,
            review.content,
            review.displayContent,
            review.reviewStatus,
            review.optionNameSnapshot,
            product.publicId,
            productVersion.nameSnapshot,
            productVersion.thumbnailKeySnapshot,
            review.createdAt,
        )

    private fun unreviewedOrderItemProjection(): Expression<UnreviewedOrderItemView> =
        Projections.constructor(
            UnreviewedOrderItemView::class.java,
            orderItem.id,
            product.publicId,
            productVersion.nameSnapshot,
            productVersion.thumbnailKeySnapshot,
            orderItem.optionNameSnapshot,
            order.purchaseConfirmedAt,
        )
}
