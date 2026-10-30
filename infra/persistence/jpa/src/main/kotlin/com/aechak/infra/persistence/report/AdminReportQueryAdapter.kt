package com.aechak.infra.persistence.report

import com.aechak.application.report.admin.port.AdminReportQueryPort
import com.aechak.application.report.admin.port.ReportPageCondition
import com.aechak.application.report.admin.port.ReportStatusCondition
import com.aechak.application.report.admin.port.view.ReportRowView
import com.aechak.application.report.admin.port.view.ReportTargetCountView
import com.aechak.domain.product.product.QProduct
import com.aechak.domain.product.report.QProductReport
import com.aechak.domain.report.decision.QReportDecision
import com.aechak.domain.report.decision.enums.ReportDecisionType
import com.aechak.domain.report.decision.enums.ReportTargetType
import com.aechak.domain.review.report.QReviewReport
import com.aechak.domain.review.review.QReview
import com.aechak.domain.user.report.QUserReport
import com.aechak.domain.user.user.QUser
import com.querydsl.core.Tuple
import com.querydsl.core.types.EntityPath
import com.querydsl.core.types.Predicate
import com.querydsl.core.types.dsl.BooleanExpression
import com.querydsl.core.types.dsl.DateTimePath
import com.querydsl.core.types.dsl.NumberPath
import com.querydsl.jpa.impl.JPAQuery
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

private val productReport = QProductReport.productReport
private val reviewReport = QReviewReport.reviewReport
private val userReport = QUserReport.userReport
private val product = QProduct.product
private val review = QReview.review
private val targetUser = QUser.user
private val decision = QReportDecision.reportDecision

/**
 * 어드민 신고 조회 포트의 QueryDSL 어댑터.
 *
 * 세 신고 표를 UNION 하지 않고 종류마다 따로 읽는다 — 호출부가 전역 정렬 순서로 병합한다.
 * 처리 상태는 report_decisions 를 left join 해 판정한다(행이 없으면 미처리).
 */
@Repository
class AdminReportQueryAdapter(
    private val queryFactory: JPAQueryFactory,
) : AdminReportQueryPort {
    override fun findPage(
        targetType: ReportTargetType,
        condition: ReportPageCondition,
    ): List<ReportRowView> = fetchRows(targetType, condition, extraWhere = null)

    override fun count(
        targetType: ReportTargetType,
        condition: ReportPageCondition,
    ): Long {
        val reportId = reportIdPath(targetType)
        return queryFactory
            .select(reportId.count())
            .from(rootOf(targetType))
            .leftJoin(decision)
            .on(decisionJoin(targetType, reportId))
            .where(
                statusMatches(condition.statuses),
                reportedAtRange(createdAtPath(targetType), condition),
            ).fetchOne() ?: 0L
    }

    override fun findOne(
        targetType: ReportTargetType,
        reportId: Long,
    ): ReportRowView? =
        fetchRows(
            targetType,
            unfiltered(targetType, limit = 1),
            extraWhere = reportIdPath(targetType).eq(reportId),
        ).firstOrNull()

    override fun countByTargets(
        targetType: ReportTargetType,
        targetIds: Collection<Long>,
    ): List<ReportTargetCountView> {
        if (targetIds.isEmpty()) return emptyList()
        val targetId = targetIdPath(targetType)
        return queryFactory
            .select(targetId, targetId.count())
            .from(rootOf(targetType))
            .where(targetId.`in`(targetIds))
            .groupBy(targetId)
            .fetch()
            .mapNotNull { tuple ->
                val id = tuple.get(targetId) ?: return@mapNotNull null
                ReportTargetCountView(targetType, id, tuple.get(targetId.count()) ?: 0L)
            }
    }

    override fun findHistoryByTarget(
        targetType: ReportTargetType,
        targetId: Long,
        limit: Int,
    ): List<ReportRowView> =
        fetchRows(
            targetType,
            unfiltered(targetType, limit),
            extraWhere = targetIdPath(targetType).eq(targetId),
        )

    /** 결정 left join, 상태·기간 필터, keyset, 전역 정렬, limit 을 한 자리에서 건다. */
    private fun fetchRows(
        targetType: ReportTargetType,
        condition: ReportPageCondition,
        extraWhere: Predicate?,
    ): List<ReportRowView> {
        val reportId = reportIdPath(targetType)
        val createdAt = createdAtPath(targetType)
        return selectFrom(targetType)
            .leftJoin(decision)
            .on(decisionJoin(targetType, reportId))
            .where(
                statusMatches(condition.statuses),
                reportedAtRange(createdAt, condition),
                keyset(targetType, createdAt, reportId, condition),
                extraWhere,
            ).orderBy(createdAt.desc(), reportId.desc())
            .limit(condition.limit.toLong())
            .fetch()
            .map { toRow(targetType, it) }
    }

    /** 종류별 select·from·join. 대상 표를 inner join 하는 이유는 신고 행이 늘 대상을 가리켜서다. */
    private fun selectFrom(targetType: ReportTargetType): JPAQuery<Tuple> =
        when (targetType) {
            ReportTargetType.PRODUCT -> {
                queryFactory
                    .select(
                        productReport.id,
                        productReport.reporterId,
                        productReport.reasonCode,
                        productReport.reasonText,
                        productReport.createdAt,
                        product.id,
                        product.publicId,
                        product.name,
                        product.sellerId,
                        decision.decision,
                        decision.reasonCode,
                        decision.createdAt,
                        decision.actedByAdminId,
                    ).from(productReport)
                    .join(product)
                    .on(product.id.eq(productReport.product.id))
            }

            ReportTargetType.REVIEW -> {
                queryFactory
                    .select(
                        reviewReport.id,
                        reviewReport.reporterId,
                        reviewReport.reasonCode,
                        reviewReport.reasonText,
                        reviewReport.createdAt,
                        review.id,
                        review.content,
                        review.authorUserId,
                        decision.decision,
                        decision.reasonCode,
                        decision.createdAt,
                        decision.actedByAdminId,
                    ).from(reviewReport)
                    .join(review)
                    .on(review.id.eq(reviewReport.review.id))
            }

            ReportTargetType.USER -> {
                queryFactory
                    .select(
                        userReport.id,
                        userReport.reporter.id,
                        userReport.reasonCode,
                        userReport.reasonText,
                        userReport.createdAt,
                        targetUser.id,
                        targetUser.publicId,
                        decision.decision,
                        decision.reasonCode,
                        decision.createdAt,
                        decision.actedByAdminId,
                    ).from(userReport)
                    .join(targetUser)
                    .on(targetUser.id.eq(userReport.targetUser.id))
            }
        }

    private fun toRow(
        targetType: ReportTargetType,
        tuple: Tuple,
    ): ReportRowView =
        when (targetType) {
            ReportTargetType.PRODUCT -> {
                ReportRowView(
                    targetType = ReportTargetType.PRODUCT,
                    reportId = tuple.get(productReport.id)!!,
                    reporterId = tuple.get(productReport.reporterId)!!,
                    reasonCode = tuple.get(productReport.reasonCode)!!.name,
                    reasonText = tuple.get(productReport.reasonText),
                    reportedAt = tuple.get(productReport.createdAt)!!,
                    targetId = tuple.get(product.id)!!,
                    targetKey = tuple.get(product.publicId)!!,
                    targetName = tuple.get(product.name),
                    targetOwnerUserId = tuple.get(product.sellerId)!!,
                    decision = tuple.get(decision.decision),
                    decisionReasonCode = tuple.get(decision.reasonCode),
                    decidedAt = tuple.get(decision.createdAt),
                    decidedByAdminId = tuple.get(decision.actedByAdminId),
                )
            }

            ReportTargetType.REVIEW -> {
                ReportRowView(
                    targetType = ReportTargetType.REVIEW,
                    reportId = tuple.get(reviewReport.id)!!,
                    reporterId = tuple.get(reviewReport.reporterId)!!,
                    reasonCode = tuple.get(reviewReport.reasonCode)!!.name,
                    reasonText = tuple.get(reviewReport.reasonText),
                    reportedAt = tuple.get(reviewReport.createdAt)!!,
                    targetId = tuple.get(review.id)!!,
                    targetKey = tuple.get(review.id)!!.toString(),
                    targetName = tuple.get(review.content),
                    targetOwnerUserId = tuple.get(review.authorUserId)!!,
                    decision = tuple.get(decision.decision),
                    decisionReasonCode = tuple.get(decision.reasonCode),
                    decidedAt = tuple.get(decision.createdAt),
                    decidedByAdminId = tuple.get(decision.actedByAdminId),
                )
            }

            ReportTargetType.USER -> {
                ReportRowView(
                    targetType = ReportTargetType.USER,
                    reportId = tuple.get(userReport.id)!!,
                    reporterId = tuple.get(userReport.reporter.id)!!,
                    reasonCode = tuple.get(userReport.reasonCode)!!.name,
                    reasonText = tuple.get(userReport.reasonText),
                    reportedAt = tuple.get(userReport.createdAt)!!,
                    targetId = tuple.get(targetUser.id)!!,
                    targetKey = tuple.get(targetUser.publicId)!!,
                    // 사용자의 표시 이름은 user_profiles 에 있어 호출부가 닉네임으로 채운다
                    targetName = null,
                    targetOwnerUserId = tuple.get(targetUser.id)!!,
                    decision = tuple.get(decision.decision),
                    decisionReasonCode = tuple.get(decision.reasonCode),
                    decidedAt = tuple.get(decision.createdAt),
                    decidedByAdminId = tuple.get(decision.actedByAdminId),
                )
            }
        }

    private fun decisionJoin(
        targetType: ReportTargetType,
        reportId: NumberPath<Long>,
    ): Predicate = decision.targetType.eq(targetType).and(decision.reportId.eq(reportId))

    private fun statusMatches(status: ReportStatusCondition): Predicate? =
        when (status) {
            ReportStatusCondition.ANY -> {
                null
            }

            ReportStatusCondition.UNDECIDED -> {
                decision.id.isNull
            }

            ReportStatusCondition.DISMISSED -> {
                decision.decision.eq(ReportDecisionType.DISMISS)
            }

            ReportStatusCondition.SANCTIONED -> {
                decision.decision.`in`(ReportDecisionType.HIDE_TARGET, ReportDecisionType.SANCTION_ACCOUNT)
            }
        }

    private fun reportedAtRange(
        createdAt: DateTimePath<LocalDateTime>,
        condition: ReportPageCondition,
    ): Predicate? {
        val from = condition.reportedFrom?.let { createdAt.goe(it) }
        val to = condition.reportedToExclusive?.let { createdAt.lt(it) }
        return when {
            from != null && to != null -> from.and(to)
            else -> from ?: to
        }
    }

    /**
     * 전역 순서(접수 역순 → 종류 이름순 → 신고 id 역순)에서 앵커보다 뒤를 고른다.
     * 한 표 안에서는 종류가 상수라 종류 비교 결과에 따라 조건이 세 모양으로 접힌다.
     */
    private fun keyset(
        targetType: ReportTargetType,
        createdAt: DateTimePath<LocalDateTime>,
        reportId: NumberPath<Long>,
        condition: ReportPageCondition,
    ): BooleanExpression? {
        val anchorAt = condition.lastReportedAt ?: return null
        val anchorType = condition.lastTargetType ?: return null
        val anchorId = condition.lastReportId ?: return null
        return when {
            // 같은 시각이면 이 표가 앵커 표보다 뒤에 오므로 동시각 행을 모두 포함한다
            targetType.name > anchorType.name -> createdAt.loe(anchorAt)

            targetType.name < anchorType.name -> createdAt.lt(anchorAt)

            else -> createdAt.lt(anchorAt).or(createdAt.eq(anchorAt).and(reportId.lt(anchorId)))
        }
    }

    /** 필터·커서 없이 limit 만 건 조건 — 단건 조회와 대상별 이력이 쓴다. */
    private fun unfiltered(
        targetType: ReportTargetType,
        limit: Int,
    ): ReportPageCondition =
        ReportPageCondition(
            targetTypes = setOf(targetType),
            statuses = ReportStatusCondition.ANY,
            reportedFrom = null,
            reportedToExclusive = null,
            lastReportedAt = null,
            lastTargetType = null,
            lastReportId = null,
            limit = limit,
        )

    private fun rootOf(targetType: ReportTargetType): EntityPath<*> =
        when (targetType) {
            ReportTargetType.PRODUCT -> productReport
            ReportTargetType.REVIEW -> reviewReport
            ReportTargetType.USER -> userReport
        }

    private fun reportIdPath(targetType: ReportTargetType): NumberPath<Long> =
        when (targetType) {
            ReportTargetType.PRODUCT -> productReport.id
            ReportTargetType.REVIEW -> reviewReport.id
            ReportTargetType.USER -> userReport.id
        }

    private fun createdAtPath(targetType: ReportTargetType): DateTimePath<LocalDateTime> =
        when (targetType) {
            ReportTargetType.PRODUCT -> productReport.createdAt
            ReportTargetType.REVIEW -> reviewReport.createdAt
            ReportTargetType.USER -> userReport.createdAt
        }

    private fun targetIdPath(targetType: ReportTargetType): NumberPath<Long> =
        when (targetType) {
            ReportTargetType.PRODUCT -> productReport.product.id
            ReportTargetType.REVIEW -> reviewReport.review.id
            ReportTargetType.USER -> userReport.targetUser.id
        }
}
