package com.aechak.infra.persistence.inquiry

import com.aechak.application.inquiry.product.port.MyProductInquiryCondition
import com.aechak.application.inquiry.product.port.ProductInquiryPageCondition
import com.aechak.application.inquiry.product.port.ProductInquiryQueryPort
import com.aechak.application.inquiry.product.port.SellerProductInquiryCondition
import com.aechak.application.inquiry.product.port.view.ProductInquiryView
import com.aechak.application.inquiry.product.port.view.SellerProductInquiryMetricsView
import com.aechak.application.inquiry.product.port.view.SellerUnansweredInquiryCountView
import com.aechak.application.inquiry.product.usecase.query.SellerUnansweredInquiryQuery
import com.aechak.domain.inquiry.product.QProductInquiry
import com.aechak.domain.inquiry.product.QProductInquiryAnswer
import com.aechak.domain.inquiry.product.enums.ProductInquiryCategory
import com.aechak.domain.inquiry.product.enums.ProductInquiryStatus
import com.querydsl.core.types.Expression
import com.querydsl.core.types.Predicate
import com.querydsl.core.types.Projections
import com.querydsl.core.types.dsl.BooleanExpression
import com.querydsl.core.types.dsl.CaseBuilder
import com.querydsl.core.types.dsl.Expressions
import com.querydsl.core.types.dsl.NumberExpression
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

private val inquiry = QProductInquiry.productInquiry
private val inquiryAnswer = QProductInquiryAnswer.productInquiryAnswer

/** 셀러와 상품 상세가 보는 범위 — 철회된 문의는 질문이 거둬진 것이라 양쪽 모두에서 빠진다. */
private val LIVE_STATUSES = listOf(ProductInquiryStatus.RECEIVED, ProductInquiryStatus.ANSWERED)

@Repository
class ProductInquiryQueryAdapter(
    private val queryFactory: JPAQueryFactory,
) : ProductInquiryQueryPort {
    override fun findProductPage(condition: ProductInquiryPageCondition): List<ProductInquiryView> =
        pageQuery(inquiry.productId.eq(condition.productId), live(), offset = condition.offset, limit = condition.limit)

    override fun countProduct(productId: Long): Long = count(inquiry.productId.eq(productId), live())

    override fun findMyPage(condition: MyProductInquiryCondition): List<ProductInquiryView> =
        pageQuery(
            inquiry.authorUserId.eq(condition.authorUserId),
            statusEq(condition.status),
            offset = condition.offset,
            limit = condition.limit,
        )

    override fun countMine(condition: MyProductInquiryCondition): Long =
        count(inquiry.authorUserId.eq(condition.authorUserId), statusEq(condition.status))

    override fun findSellerPage(condition: SellerProductInquiryCondition): List<ProductInquiryView> =
        pageQuery(*sellerFilters(condition), offset = condition.offset, limit = condition.limit)

    override fun countSeller(condition: SellerProductInquiryCondition): Long = count(*sellerFilters(condition))

    /**
     * 지표 세 개를 조건부 합계로 한 번에 센다. 기한 지남은 저장된 기한과 넘겨받은 서버 시각만으로
     * 판정한다 — 목록 행의 overdue와 같은 규칙이라 두 숫자가 어긋나지 않는다.
     */
    override fun countSellerMetrics(
        sellerId: Long,
        now: LocalDateTime,
    ): SellerProductInquiryMetricsView {
        val pending = inquiry.status.eq(ProductInquiryStatus.RECEIVED)
        return queryFactory
            .select(
                Projections.constructor(
                    SellerProductInquiryMetricsView::class.java,
                    countWhere(pending),
                    countWhere(pending.and(inquiry.answerDueAt.lt(now))),
                    countWhere(inquiry.status.`in`(LIVE_STATUSES)),
                ),
            ).from(inquiry)
            .where(inquiry.sellerId.eq(sellerId))
            .fetchOne()!!
    }

    /**
     * 직전 구간이 없으면 건수 한 번, 있으면 두 구간을 조건부 합계로 한 번에 센다. WHERE는 두 구간의 합집합
     * [previousAskedFrom, askedToExclusive)로 먼저 좁혀 답변 대기 전체를 훑지 않는다.
     */
    override fun countSellerUnanswered(query: SellerUnansweredInquiryQuery): SellerUnansweredInquiryCountView {
        val unanswered = inquiry.sellerId.eq(query.sellerId).and(inquiry.status.eq(ProductInquiryStatus.RECEIVED))
        val toExclusive = query.askedToExclusive?.let { inquiry.createdAt.lt(it) }
        val previousFrom = query.previousAskedFrom
        val currentFrom = query.askedFrom
        if (previousFrom == null || currentFrom == null) {
            val current = count(unanswered, currentFrom?.let { inquiry.createdAt.goe(it) }, toExclusive)
            return SellerUnansweredInquiryCountView(current = current, previous = null)
        }
        val inCurrent = inquiry.createdAt.goe(currentFrom)
        val inPrevious = inquiry.createdAt.goe(previousFrom).and(inquiry.createdAt.lt(currentFrom))
        val counts =
            queryFactory
                .select(countWhere(inCurrent), countWhere(inPrevious))
                .from(inquiry)
                .where(unanswered, inquiry.createdAt.goe(previousFrom), toExclusive)
                .fetchOne()!!
        return SellerUnansweredInquiryCountView(
            current = counts.get(0, Long::class.javaObjectType) ?: 0L,
            previous = counts.get(1, Long::class.javaObjectType) ?: 0L,
        )
    }

    private fun pageQuery(
        vararg filters: Predicate?,
        offset: Long,
        limit: Int,
    ): List<ProductInquiryView> =
        queryFactory
            .select(viewProjection())
            .from(inquiry)
            .leftJoin(inquiry._answers, inquiryAnswer)
            .where(*filters)
            .orderBy(inquiry.id.desc())
            .offset(offset)
            .limit(limit.toLong())
            .fetch()

    private fun count(vararg filters: Predicate?): Long =
        queryFactory
            .select(inquiry.count())
            .from(inquiry)
            .where(*filters)
            .fetchOne() ?: 0L

    private fun sellerFilters(condition: SellerProductInquiryCondition): Array<Predicate?> =
        arrayOf(
            inquiry.sellerId.eq(condition.sellerId),
            condition.status?.let { inquiry.status.eq(it.status) } ?: live(),
            categoryEq(condition.category),
            keywordContains(condition.keyword),
        )

    private fun live(): BooleanExpression = inquiry.status.`in`(LIVE_STATUSES)

    private fun statusEq(status: ProductInquiryStatus?): Predicate? = status?.let { inquiry.status.eq(it) }

    private fun categoryEq(category: ProductInquiryCategory?): Predicate? = category?.let { inquiry.category.eq(it) }

    /** 화면의 "문의 제목이나 상품명으로 찾기"와 같은 범위다. */
    private fun keywordContains(keyword: String?): Predicate? =
        keyword?.let {
            inquiry.title.containsIgnoreCase(it).or(inquiry.productNameSnapshot.containsIgnoreCase(it))
        }

    /** 해당 셀러에 문의가 없으면 집계가 NULL이다 — 0은 쿼리에서 못박는다. */
    private fun countWhere(predicate: BooleanExpression): NumberExpression<Long> =
        Expressions.numberTemplate(
            Long::class.javaObjectType,
            "coalesce(sum({0}), 0)",
            CaseBuilder().`when`(predicate).then(1L).otherwise(0L),
        )

    private fun viewProjection(): Expression<ProductInquiryView> =
        Projections.constructor(
            ProductInquiryView::class.java,
            inquiry.id,
            inquiry.publicId,
            inquiry.productId,
            inquiry.productPublicId,
            inquiry.sellerId,
            inquiry.authorUserId,
            inquiry.productNameSnapshot,
            inquiry.category,
            inquiry.title,
            inquiry.content,
            inquiry.secret,
            inquiry.status,
            inquiry.answerDueAt,
            inquiry.answeredAt,
            inquiry.createdAt,
            inquiryAnswer.content,
        )
}
