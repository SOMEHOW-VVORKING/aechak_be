package com.aechak.infra.persistence.seller

import com.aechak.application.seller.port.AdminSellerPageCondition
import com.aechak.application.seller.port.AdminSellerQueryPort
import com.aechak.application.seller.port.view.AdminSellerView
import com.aechak.application.seller.port.view.SellerSanctionView
import com.aechak.domain.product.product.QProduct
import com.aechak.domain.seller.application.QSellerApplication
import com.aechak.domain.seller.application.enums.BusinessType
import com.aechak.domain.seller.sanction.QSellerSanction
import com.aechak.domain.seller.seller.QSeller
import com.aechak.domain.seller.seller.enums.SellerStatus
import com.aechak.domain.settlement.settlement.QSettlement
import com.aechak.domain.settlement.settlement.enums.SettlementStatus
import com.querydsl.core.types.Expression
import com.querydsl.core.types.Predicate
import com.querydsl.core.types.Projections
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

private val seller = QSeller.seller
private val application = QSellerApplication.sellerApplication
private val product = QProduct.product
private val settlement = QSettlement.settlement
private val sanction = QSellerSanction.sellerSanction

/**
 * 어드민 셀러 계정 조회 포트의 QueryDSL 어댑터.
 *
 * 사업자 유형·대표자명은 sellers에 없어 입점 신청서를 left join으로 붙인다 — 신청서가 없는 셀러
 * (시딩·과거 데이터)도 목록에서 빠지면 안 된다. 신청 상태 조건을 걸지 않는 이유는 신청서가 유저당
 * 1행 재사용 모델이고 기셀러의 재신청이 막혀 있어(10001) 셀러의 신청서 행이 곧 승인된 그 행이기 때문이다.
 *
 * 상품 수와 미정산 여부는 행마다 세지 않는다. 페이지가 정해진 뒤 그 셀러 id 목록으로 한 번에 묻는다 —
 * 상관 서브쿼리는 행마다 1회 실행이고, 두 집계는 서로 다른 테이블이라 한 쿼리로 묶으면
 * 상품이 0개인 셀러가 조인에서 사라진다(AdminMemberQueryAdapter의 신고 수와 같은 방식).
 */
@Repository
class AdminSellerQueryAdapter(
    private val queryFactory: JPAQueryFactory,
) : AdminSellerQueryPort {
    override fun findSellerPage(condition: AdminSellerPageCondition): List<AdminSellerView> =
        withAggregates(
            queryFactory
                .select(sellerRowProjection())
                .from(seller)
                .leftJoin(application)
                .on(application.userId.eq(seller.userId))
                .where(
                    statusEq(condition.status),
                    businessTypeIn(condition.businessTypes),
                    storeNameContains(condition.storeNameKeyword),
                ).orderBy(seller.userId.desc())
                .offset(condition.offset)
                .limit(condition.limit.toLong())
                .fetch(),
        )

    override fun countSellers(condition: AdminSellerPageCondition): Long =
        queryFactory
            .select(seller.count())
            .from(seller)
            .leftJoin(application)
            .on(application.userId.eq(seller.userId))
            .where(
                statusEq(condition.status),
                businessTypeIn(condition.businessTypes),
                storeNameContains(condition.storeNameKeyword),
            ).fetchOne() ?: 0L

    override fun findSeller(sellerId: Long): AdminSellerView? =
        withAggregates(
            queryFactory
                .select(sellerRowProjection())
                .from(seller)
                .leftJoin(application)
                .on(application.userId.eq(seller.userId))
                .where(seller.userId.eq(sellerId))
                .fetch(),
        ).firstOrNull()

    override fun findSanctionHistory(
        sellerId: Long,
        limit: Int,
    ): List<SellerSanctionView> =
        queryFactory
            .select(
                Projections.constructor(
                    SellerSanctionView::class.java,
                    sanction.id,
                    sanction.action,
                    sanction.reasonCode,
                    sanction.reasonDetail,
                    sanction.effectiveUntil,
                    sanction.memo,
                    sanction.actedByAdminId,
                    sanction.createdAt,
                ),
            ).from(sanction)
            .where(sanction.sellerId.eq(sellerId))
            // 이력은 추가 전용이라 id 내림차순이 곧 최신순이다
            .orderBy(sanction.id.desc())
            .limit(limit.toLong())
            .fetch()

    /** 상품 수와 미정산 여부를 페이지 분량만 묶어 각각 한 번씩 조회해 붙인다. */
    private fun withAggregates(rows: List<SellerRowProjection>): List<AdminSellerView> {
        if (rows.isEmpty()) return emptyList()
        val sellerIds = rows.map { it.sellerId }
        val productCounts = productCountsOf(sellerIds)
        val unsettled = unsettledSellerIdsOf(sellerIds)
        return rows.map {
            AdminSellerView(
                it.sellerId,
                it.storeName,
                it.status,
                it.statusChangedAt,
                it.suspensionUntil,
                it.suspensionReason,
                it.adminMemo,
                it.confirmedOrderCount,
                it.businessType,
                it.representativeName,
                productCounts[it.sellerId] ?: 0L,
                it.sellerId in unsettled,
            )
        }
    }

    private fun productCountsOf(sellerIds: List<Long>): Map<Long, Long> =
        queryFactory
            .select(product.sellerId, product.count())
            .from(product)
            .where(product.sellerId.`in`(sellerIds))
            .groupBy(product.sellerId)
            .fetch()
            .mapNotNull { tuple ->
                val id = tuple.get(product.sellerId) ?: return@mapNotNull null
                id to (tuple.get(product.count()) ?: 0L)
            }.toMap()

    /** 지급 대기 원장이 한 행이라도 있는 셀러 — 금액을 합하지 않는다(화면이 묻는 것은 보유 여부다). */
    private fun unsettledSellerIdsOf(sellerIds: List<Long>): Set<Long> =
        queryFactory
            .select(settlement.sellerId)
            .distinct()
            .from(settlement)
            .where(
                settlement.sellerId.`in`(sellerIds),
                settlement.status.eq(SettlementStatus.PENDING),
            ).fetch()
            .filterNotNull()
            .toSet()

    private fun statusEq(status: SellerStatus?): Predicate? = status?.let { seller.status.eq(it) }

    /** 유형 조건이 걸리면 신청서가 없는 셀러는 빠진다 — 유형을 모르는 셀러를 어느 쪽으로도 세지 않는다. */
    private fun businessTypeIn(businessTypes: List<BusinessType>): Predicate? =
        businessTypes.takeIf { it.isNotEmpty() }?.let { application.businessType.`in`(it) }

    private fun storeNameContains(keyword: String?): Predicate? = keyword?.let { seller.storeName.contains(it) }

    private fun sellerRowProjection(): Expression<SellerRowProjection> =
        Projections.constructor(
            SellerRowProjection::class.java,
            seller.userId,
            seller.storeName,
            seller.status,
            seller.statusChangedAt,
            seller.suspensionUntil,
            seller.suspensionReason,
            seller.adminMemo,
            seller.confirmedOrderCount,
            application.businessType,
            application.representativeName,
        )
}

/** 어댑터 내부 전용 — 상품 수·미정산 여부를 채우기 전의 한 행. */
data class SellerRowProjection(
    val sellerId: Long,
    val storeName: String,
    val status: SellerStatus,
    val statusChangedAt: LocalDateTime,
    val suspensionUntil: LocalDateTime?,
    val suspensionReason: String?,
    val adminMemo: String?,
    val confirmedOrderCount: Int,
    val businessType: BusinessType?,
    val representativeName: String?,
)
