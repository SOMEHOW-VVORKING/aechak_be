package com.aechak.infra.persistence.product

import com.aechak.application.product.product.admin.port.AdminProductPageCondition
import com.aechak.application.product.product.admin.port.AdminProductQueryPort
import com.aechak.application.product.product.admin.port.view.AdminProductView
import com.aechak.domain.product.product.enums.InspectionStatus
import com.aechak.domain.product.product.enums.SaleStatus
import com.aechak.domain.product.report.QProductReport
import com.querydsl.core.types.Expression
import com.querydsl.core.types.Predicate
import com.querydsl.core.types.Projections
import com.querydsl.jpa.impl.JPAQuery
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

private val productReport = QProductReport.productReport

/**
 * 어드민 상품 전수 조회의 QueryDSL 어댑터.
 *
 * 구매자 노출 조건을 걸지 않는다 — 검수 상태·판매 상태·카테고리 활성·셀러 활성 넷 모두 필터가 아니라
 * 화면이 보여 주는 값이다. 셀러 상호명은 sellers를 left join으로 붙인다: products.seller_id는 FK 없는
 * 값 참조라 셀러 행이 없는 상품을 목록에서 떨어뜨리면 운영자가 그 상품에 손을 댈 수 없게 된다.
 *
 * 신고 수는 행마다 세지 않는다. 페이지가 정해진 뒤 그 상품 id 목록으로 한 번에 묻는다 — 상관 서브쿼리는
 * 행마다 1회 실행이고, 본 질의에 group by로 섞으면 신고가 0건인 상품이 집계에서 사라진다.
 */
@Repository
class AdminProductQueryAdapter(
    private val queryFactory: JPAQueryFactory,
) : AdminProductQueryPort {
    override fun findProductPage(condition: AdminProductPageCondition): List<AdminProductView> =
        withReportCounts(
            adminProductQuery(rowProjection(), condition)
                .orderBy(product.id.desc())
                .offset(condition.offset)
                .limit(condition.limit.toLong())
                .fetch(),
        )

    override fun countProducts(condition: AdminProductPageCondition): Long = adminProductQuery(product.count(), condition).fetchOne() ?: 0L

    /**
     * 조회와 집계가 같은 조인·조건을 쓰게 한곳에 둔다.
     * 카테고리는 이름을 실어야 해 inner join이고(상품은 카테고리를 NOT NULL로 든다),
     * 부모·조부모는 카테고리 필터의 하위 포함 판정에만 쓰여 left join이다.
     */
    private fun <T> adminProductQuery(
        select: Expression<T>,
        condition: AdminProductPageCondition,
    ): JPAQuery<T> =
        queryFactory
            .select(select)
            .from(product)
            .join(product.category, category)
            .leftJoin(category.parent, parent)
            .leftJoin(parent.parent, grandParent)
            .leftJoin(seller)
            .on(seller.userId.eq(product.sellerId))
            .where(
                inspectionStatusEq(condition.inspectionStatus),
                saleStatusEq(condition.saleStatus),
                categorySubtree(condition.categoryId),
                keywordMatches(condition.keyword),
            )

    private fun inspectionStatusEq(status: InspectionStatus?): Predicate? = status?.let { product.inspectionStatus.eq(it) }

    private fun saleStatusEq(status: SaleStatus?): Predicate? = status?.let { product.saleStatus.eq(it) }

    /** 대·중·소 어느 단계를 줘도 그 아래 상품이 다 잡힌다 — 카테고리 깊이가 3이라 세 칸으로 덮인다. */
    private fun categorySubtree(categoryId: Long?): Predicate? =
        categoryId?.let {
            category.id
                .eq(it)
                .or(parent.id.eq(it))
                .or(grandParent.id.eq(it))
        }

    /** 상품명과 상호명을 한 칸으로 받는다 — 운영자가 어느 쪽을 적었는지 고르게 하지 않는다. */
    private fun keywordMatches(keyword: String?): Predicate? = keyword?.let { product.name.contains(it).or(seller.storeName.contains(it)) }

    private fun withReportCounts(rows: List<AdminProductRowProjection>): List<AdminProductView> {
        if (rows.isEmpty()) return emptyList()
        val counts = reportCountsOf(rows.map { it.productId })
        return rows.map {
            AdminProductView(
                it.productId,
                it.productPublicId,
                it.productName,
                it.sellerId,
                it.storeName,
                it.categoryName,
                it.regularPrice,
                it.discountPrice,
                it.saleStatus,
                it.inspectionStatus,
                it.createdAt,
                it.adminSaleBlockedAt,
                it.adminSaleBlockReason,
                counts[it.productId] ?: 0L,
            )
        }
    }

    /** 처리 상태로 거르지 않는다 — 화면이 묻는 것은 "이 상품에 신고가 몇 번 있었나"다. */
    private fun reportCountsOf(productIds: List<Long>): Map<Long, Long> =
        queryFactory
            .select(productReport.product.id, productReport.count())
            .from(productReport)
            .where(productReport.product.id.`in`(productIds))
            .groupBy(productReport.product.id)
            .fetch()
            .mapNotNull { tuple ->
                val id = tuple.get(productReport.product.id) ?: return@mapNotNull null
                id to (tuple.get(productReport.count()) ?: 0L)
            }.toMap()

    private fun rowProjection(): Expression<AdminProductRowProjection> =
        Projections.constructor(
            AdminProductRowProjection::class.java,
            product.id,
            product.publicId,
            product.name,
            product.sellerId,
            seller.storeName,
            category.name,
            product.regularPrice,
            product.discountPrice,
            product.saleStatus,
            product.inspectionStatus,
            product.createdAt,
            product.adminSaleBlockedAt,
            product.adminSaleBlockReason,
        )
}

/** 어댑터 내부 전용 — 신고 수를 채우기 전의 한 행. */
data class AdminProductRowProjection(
    val productId: Long,
    val productPublicId: String,
    val productName: String,
    val sellerId: Long,
    val storeName: String?,
    val categoryName: String,
    val regularPrice: Long,
    val discountPrice: Long?,
    val saleStatus: SaleStatus,
    val inspectionStatus: InspectionStatus,
    val createdAt: LocalDateTime,
    val adminSaleBlockedAt: LocalDateTime?,
    val adminSaleBlockReason: String?,
)
