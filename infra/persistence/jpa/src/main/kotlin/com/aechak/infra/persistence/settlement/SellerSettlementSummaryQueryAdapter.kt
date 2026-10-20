package com.aechak.infra.persistence.settlement

import com.aechak.application.settlement.seller.port.SellerSettlementSummaryQueryPort
import com.aechak.application.settlement.seller.port.view.SellerSettlementSummaryView
import com.aechak.domain.settlement.settlement.QSettlement
import com.aechak.domain.settlement.settlement.enums.SettlementStatus
import com.querydsl.core.types.Projections
import com.querydsl.core.types.dsl.BooleanExpression
import com.querydsl.core.types.dsl.CaseBuilder
import com.querydsl.core.types.dsl.Expressions
import com.querydsl.core.types.dsl.NumberExpression
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Repository

private val settlement = QSettlement.settlement

/**
 * 셀러의 미지급 정산액을 상태별 조건부 합계로 한 번에 센다.
 * 지급 완료·취소 행은 어느 금액에도 들어가지 않는다 — 화면이 묻는 것은 앞으로 받을 돈이다.
 */
@Repository
class SellerSettlementSummaryQueryAdapter(
    private val queryFactory: JPAQueryFactory,
) : SellerSettlementSummaryQueryPort {
    override fun findSummary(sellerId: Long): SellerSettlementSummaryView =
        queryFactory
            .select(
                Projections.constructor(
                    SellerSettlementSummaryView::class.java,
                    sumWhere(settlement.status.eq(SettlementStatus.PENDING)),
                    sumWhere(settlement.status.eq(SettlementStatus.ON_HOLD)),
                ),
            ).from(settlement)
            .where(settlement.sellerId.eq(sellerId))
            .fetchOne()!!

    /** 정산 행이 없는 셀러에서는 집계가 NULL이다 — 0은 쿼리에서 못박고 투영은 값이 있다고만 전제한다 */
    private fun sumWhere(predicate: BooleanExpression): NumberExpression<Long> =
        Expressions.numberTemplate(
            Long::class.javaObjectType,
            "coalesce(sum({0}), 0)",
            CaseBuilder().`when`(predicate).then(settlement.netAmount).otherwise(0L),
        )
}
