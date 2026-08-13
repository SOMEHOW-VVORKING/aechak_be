package com.aechak.infra.persistence.product

import com.aechak.application.product.like.port.LikedProductCondition
import com.aechak.application.product.like.port.LikedProductQueryPort
import com.aechak.application.product.like.port.view.LikedProductView
import com.aechak.application.product.product.port.view.ProductCatalogView
import com.aechak.domain.product.like.QProductLike
import com.aechak.domain.product.product.enums.InspectionStatus
import com.aechak.domain.product.product.enums.SaleStatus
import com.aechak.domain.seller.seller.enums.SellerStatus
import com.querydsl.core.types.Expression
import com.querydsl.core.types.Predicate
import com.querydsl.core.types.Projections
import com.querydsl.core.types.dsl.CaseBuilder
import com.querydsl.core.types.dsl.NumberExpression
import com.querydsl.jpa.impl.JPAQuery
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

internal val productLike = QProductLike.productLike

/** 내 찜 목록 QueryDSL 조회. 승인·판매중/품절·셀러 ACTIVE 찜 상품, 최신 찜 순 */
@Repository
class LikedProductQueryAdapter(
    private val queryFactory: JPAQueryFactory,
) : LikedProductQueryPort {
    override fun findLikedPage(condition: LikedProductCondition): List<LikedProductView> =
        likedBaseQuery(likedRowProjection(effectivePrice(condition.now)), condition.userId)
            .where(keyset(condition.lastLikeId))
            .orderBy(productLike.id.desc())
            .limit(condition.limit.toLong())
            .fetch()

    override fun countLiked(userId: Long): Long = likedBaseQuery(product.count(), userId).fetchOne() ?: 0L

    /** 노출 가능한 내 찜 상품 기본 쿼리 */
    private fun <T> likedBaseQuery(
        select: Expression<T>,
        userId: Long,
    ): JPAQuery<T> =
        queryFactory
            .select(select)
            .from(product)
            .join(seller)
            .on(seller.userId.eq(product.sellerId).and(seller.status.eq(SellerStatus.ACTIVE)))
            .join(productLike)
            .on(productLike.product.id.eq(product.id))
            .leftJoin(product.category, category)
            .leftJoin(category.parent, parent)
            .leftJoin(parent.parent, grandParent)
            .leftJoin(productStats)
            .on(productStats.productId.eq(product.id))
            .where(
                product.inspectionStatus.eq(InspectionStatus.APPROVED),
                product.saleStatus.`in`(SaleStatus.EXPOSABLE),
                productLike.userId.eq(userId),
            )

    /** 커서 앵커 likeId + 카탈로그 카드 projection */
    private fun likedRowProjection(effectivePrice: NumberExpression<Long>): Expression<LikedProductView> =
        Projections.constructor(
            LikedProductView::class.java,
            productLike.id,
            Projections.constructor(
                ProductCatalogView::class.java,
                product.id,
                product.publicId,
                product.name,
                seller.storeName,
                product.representativeImageKey,
                product.regularPrice,
                product.discountPrice,
                product.discountStartAt,
                product.discountEndAt,
                effectivePrice,
                product.saleStatus,
                productStats.averageRating,
                reviewCountScore(),
                viewable(),
            ),
        )

    /** 상세 진입 가능 여부 */
    private fun viewable(): Expression<Boolean> =
        CaseBuilder()
            .`when`(categoryChainActive())
            .then(true)
            .otherwise(false)

    /** likeId keyset 경계 (최신 찜 순) */
    private fun keyset(lastLikeId: Long?): Predicate? = lastLikeId?.let { productLike.id.lt(it) }

    /** 유효가격 (할인 기간이면 할인가, 아니면 정가) */
    private fun effectivePrice(now: LocalDateTime): NumberExpression<Long> =
        CaseBuilder()
            .`when`(
                product.discountPrice.isNotNull
                    .and(product.discountStartAt.isNull.or(product.discountStartAt.loe(now)))
                    .and(product.discountEndAt.isNull.or(product.discountEndAt.goe(now))),
            ).then(product.discountPrice)
            .otherwise(product.regularPrice)
}
