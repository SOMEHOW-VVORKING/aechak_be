package com.aechak.infra.persistence.order.claim

import com.aechak.application.order.claim.admin.port.AdminClaimCondition
import com.aechak.application.order.claim.admin.port.AdminClaimQueryPort
import com.aechak.application.order.claim.admin.port.AdminClaimSort
import com.aechak.application.order.claim.admin.port.view.AdminClaimInterventionView
import com.aechak.application.order.claim.admin.port.view.AdminClaimView
import com.aechak.application.order.claim.port.view.SellerClaimItemView
import com.aechak.domain.order.claim.QClaim
import com.aechak.domain.order.claim.QClaimIntervention
import com.aechak.domain.order.claim.QClaimItem
import com.aechak.domain.order.claim.enums.ClaimStatus
import com.aechak.domain.order.claim.enums.ClaimType
import com.aechak.domain.order.claim.enums.RefundStatus
import com.aechak.domain.order.group.QOrderGroup
import com.aechak.domain.order.order.QOrder
import com.aechak.domain.order.order.QOrderItem
import com.aechak.domain.product.version.QProductVersion
import com.aechak.domain.user.user.QUser
import com.aechak.domain.user.user.QUserProfile
import com.querydsl.core.types.Expression
import com.querydsl.core.types.Predicate
import com.querydsl.core.types.Projections
import com.querydsl.core.types.dsl.BooleanExpression
import com.querydsl.jpa.impl.JPAQuery
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

private val claim = QClaim.claim
private val claimItem = QClaimItem.claimItem
private val intervention = QClaimIntervention.claimIntervention
private val order = QOrder.order
private val orderGroup = QOrderGroup.orderGroup
private val orderItem = QOrderItem.orderItem
private val productVersion = QProductVersion.productVersion
private val user = QUser.user
private val userProfile = QUserProfile.userProfile

/**
 * 어드민 클레임 목록·상세 조회. 셀러 범위로 좁히지 않는 대신 운영자가 양쪽을 다 보고 판정할 수 있게
 * 셀러명(주문 스냅샷)과 구매자 닉네임을 같은 행에 싣는다.
 *
 * 구매자 조인이 LEFT인 이유: 닉네임은 user_profiles에 있고 온보딩 전 계정에는 그 행이 없다 —
 * INNER로 묶으면 그런 구매자의 클레임이 목록에서 통째로 사라진다.
 *
 * 정렬·키셋을 클레임 id로 하는 근거는 AdminClaimSort에 적혀 있다.
 */
@Repository
class AdminClaimQueryAdapter(
    private val queryFactory: JPAQueryFactory,
) : AdminClaimQueryPort {
    override fun findPage(condition: AdminClaimCondition): List<AdminClaimView> =
        baseQuery(viewProjection())
            .where(*(filters(condition) + cursorAfter(condition)))
            .orderBy(if (condition.sort == AdminClaimSort.OLDEST_FIRST) claim.id.asc() else claim.id.desc())
            .limit(condition.limit.toLong())
            .fetch()

    override fun count(condition: AdminClaimCondition): Long =
        baseQuery(claim.count())
            .where(*filters(condition))
            .fetchOne() ?: 0L

    override fun findOne(claimPublicId: String): AdminClaimView? =
        baseQuery(viewProjection())
            .where(claim.publicId.eq(claimPublicId))
            .fetchOne()

    override fun findItems(claimIds: Collection<Long>): List<SellerClaimItemView> {
        if (claimIds.isEmpty()) return emptyList()
        return queryFactory
            .select(
                Projections.constructor(
                    SellerClaimItemView::class.java,
                    claim.id,
                    orderItem.id,
                    productVersion.nameSnapshot,
                    orderItem.optionNameSnapshot,
                    claimItem.quantity,
                    orderItem.unitPriceSnapshot,
                    claimItem.refundAmount,
                    orderItem.itemStatus,
                ),
            ).from(claim)
            .join(claim._items, claimItem)
            .join(claimItem.orderItem, orderItem)
            .join(productVersion)
            .on(productVersion.id.eq(orderItem.productVersionId))
            .where(claim.id.`in`(claimIds))
            .orderBy(claim.id.desc(), claimItem.id.asc())
            .fetch()
    }

    override fun findInterventions(claimIds: Collection<Long>): List<AdminClaimInterventionView> {
        if (claimIds.isEmpty()) return emptyList()
        return queryFactory
            .select(
                Projections.constructor(
                    AdminClaimInterventionView::class.java,
                    intervention.claimId,
                    intervention.adminId,
                    intervention.faultDecision,
                    intervention.reason,
                    intervention.reasonDetail,
                    intervention.returnCostBearer,
                    intervention.previousStatus,
                    intervention.previousSellerApprovalStatus,
                    intervention.previousResolvedAt,
                    intervention.internalMemo,
                    intervention.createdAt,
                ),
            ).from(intervention)
            .where(intervention.claimId.`in`(claimIds))
            .fetch()
    }

    /**
     * 주문·주문그룹은 공개 식별자와 구매자를 얻기 위한 INNER 조인이다 — 클레임은 저장된 주문에만
     * 걸리므로 행이 빠지지 않는다. 구매자(users·user_profiles)만 LEFT다.
     */
    private fun <T> baseQuery(select: Expression<T>): JPAQuery<T> =
        queryFactory
            .select(select)
            .from(claim)
            .join(order)
            .on(order.id.eq(claim.orderId))
            .join(orderGroup)
            .on(orderGroup.id.eq(order.orderGroup.id))
            .leftJoin(user)
            .on(user.id.eq(orderGroup.buyerId))
            .leftJoin(userProfile)
            .on(userProfile.userId.eq(orderGroup.buyerId))

    private fun filters(condition: AdminClaimCondition): Array<Predicate?> =
        arrayOf(
            claimTypeIn(condition),
            statusIn(condition),
            requestedFrom(condition.requestedFrom),
            requestedBefore(condition.requestedToExclusive),
            orderPublicIdEq(condition.orderPublicId),
            sellerNameContains(condition.sellerKeyword),
            buyerNicknameContains(condition.buyerKeyword),
            openOnly(condition.openOnly),
        )

    private fun viewProjection(): Expression<AdminClaimView> =
        Projections.constructor(
            AdminClaimView::class.java,
            claim.id,
            claim.publicId,
            claim.orderId,
            order.orderGroup.id,
            order.publicId,
            claim.claimType,
            claim.reasonCode,
            claim.reasonDetail,
            claim.status,
            claim.refundStatus,
            claim.sellerApprovalStatus,
            claim.settlementStatus,
            claim.returnCostBearer,
            claim.rejectionReason,
            claim.sellerMemo,
            claim.adminReason,
            claim.requestedAt,
            claim.autoApproveDueAt,
            claim.approvedAt,
            claim.collectionStartedAt,
            claim.clawbackDepositedAt,
            claim.resolvedAt,
            claim.sellerId,
            order.sellerNameSnapshot,
            orderGroup.buyerId,
            user.publicId,
            userProfile.nickname,
        )

    private fun claimTypeIn(condition: AdminClaimCondition): Predicate? =
        condition.claimTypes.takeIf { it.isNotEmpty() }?.let { claim.claimType.`in`(it) }

    private fun statusIn(condition: AdminClaimCondition): Predicate? =
        condition.statuses.takeIf { it.isNotEmpty() }?.let { claim.status.`in`(it) }

    private fun requestedFrom(from: LocalDateTime?): Predicate? = from?.let { claim.requestedAt.goe(it) }

    private fun requestedBefore(toExclusive: LocalDateTime?): Predicate? = toExclusive?.let { claim.requestedAt.lt(it) }

    private fun orderPublicIdEq(orderPublicId: String?): Predicate? = orderPublicId?.let { order.publicId.eq(it) }

    // contains는 Querydsl이 상수의 %·_를 이스케이프해 글자로 넘긴다(JPQLTemplates: like {%1%} escape '!')
    private fun sellerNameContains(keyword: String?): Predicate? = keyword?.let { order.sellerNameSnapshot.contains(it) }

    private fun buyerNicknameContains(keyword: String?): Predicate? = keyword?.let { userProfile.nickname.contains(it) }

    /**
     * 운영자가 지금 개입할 수 있는 건으로 좁힌다. 상태 집합은 전이표에서 뽑은 것을 그대로 쓰고
     * 환불·교환 종결 축은 여기서 더한다 — 애그리거트 판정(Claim.isAdminOverridable)과 같은 세 축이어야
     * 목록이 개입 가능으로 보여 준 건을 실행 경로가 거절하지 않는다.
     */
    private fun openOnly(openOnly: Boolean): Predicate? =
        if (!openOnly) {
            null
        } else {
            claim.status
                .`in`(ClaimStatus.ADMIN_OVERRIDABLE)
                .and(claim.refundStatus.isNull.or(claim.refundStatus.eq(RefundStatus.NONE)))
                .and(exchangeSettled().not())
        }

    /** Claim.isExchangeSettled와 같은 조건 — 교환은 재발송으로 끝나도 상태가 APPROVED에 머문다. */
    private fun exchangeSettled(): BooleanExpression =
        claim.claimType
            .eq(ClaimType.EXCHANGE)
            .and(claim.status.eq(ClaimStatus.APPROVED))
            .and(claim.resolvedAt.isNotNull)

    /** 키셋 부등호는 정렬 방향을 따라간다 — 뒤집히면 이미 본 구간을 다시 읽거나 건너뛴다. */
    private fun cursorAfter(condition: AdminClaimCondition): Predicate? =
        condition.lastClaimId?.let {
            if (condition.sort == AdminClaimSort.OLDEST_FIRST) claim.id.gt(it) else claim.id.lt(it)
        }
}
