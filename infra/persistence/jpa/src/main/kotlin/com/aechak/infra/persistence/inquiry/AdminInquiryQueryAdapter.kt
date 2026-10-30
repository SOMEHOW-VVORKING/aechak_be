package com.aechak.infra.persistence.inquiry

import com.aechak.application.inquiry.port.AdminInquiryAnchor
import com.aechak.application.inquiry.port.AdminInquiryCondition
import com.aechak.application.inquiry.port.AdminInquiryQueryPort
import com.aechak.application.inquiry.port.view.AdminInquiryView
import com.aechak.application.inquiry.port.view.PreviousInquiryView
import com.aechak.domain.inquiry.inquiry.QInquiry
import com.aechak.domain.inquiry.inquiry.enums.InquiryStatus
import com.aechak.domain.inquiry.inquiry.enums.InquiryType
import com.querydsl.core.types.Expression
import com.querydsl.core.types.Predicate
import com.querydsl.core.types.Projections
import com.querydsl.jpa.impl.JPAQuery
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

private val inquiry = QInquiry.inquiry

/**
 * 운영자용 문의 목록·이력 조회를 QueryDSL로 수행한다.
 * 정렬은 접수 오래된 순(created_at, id) 고정 — 오래 묵은 문의가 먼저 보여야 운영이 밀리지 않는다.
 * 키워드는 제목이 없는 문의 특성상 내용과 답변 수신 이메일을 함께 훑는다.
 */
@Repository
class AdminInquiryQueryAdapter(
    private val queryFactory: JPAQueryFactory,
) : AdminInquiryQueryPort {
    override fun findPage(condition: AdminInquiryCondition): List<AdminInquiryView> =
        filtered(condition, viewProjection())
            .where(afterAnchor(condition.anchor))
            .orderBy(inquiry.createdAt.asc(), inquiry.id.asc())
            .limit(condition.limit.toLong())
            .fetch()

    override fun count(condition: AdminInquiryCondition): Long = filtered(condition, inquiry.count()).fetchOne() ?: 0L

    override fun findPreviousByUserId(
        userId: Long,
        beforeCreatedAt: LocalDateTime,
        beforeInquiryId: Long,
        limit: Int,
    ): List<PreviousInquiryView> =
        queryFactory
            .select(
                Projections.constructor(
                    PreviousInquiryView::class.java,
                    inquiry.id,
                    inquiry.inquiryType,
                    inquiry.status,
                    inquiry.createdAt,
                ),
            ).from(inquiry)
            .where(inquiry.userId.eq(userId), beforeAnchor(beforeCreatedAt, beforeInquiryId))
            .orderBy(inquiry.createdAt.desc(), inquiry.id.desc())
            .limit(limit.toLong())
            .fetch()

    /** 목록·카운트가 공유하는 필터 골격. 커서 앵커는 목록에만 걸린다 — 총건수는 페이지와 무관하다. */
    private fun <T> filtered(
        condition: AdminInquiryCondition,
        select: Expression<T>,
    ): JPAQuery<T> =
        queryFactory
            .select(select)
            .from(inquiry)
            .where(
                statusEq(condition.status),
                inquiryTypeEq(condition.inquiryType),
                keywordContains(condition.keyword),
            )

    private fun viewProjection(): Expression<AdminInquiryView> =
        Projections.constructor(
            AdminInquiryView::class.java,
            inquiry.id,
            inquiry.inquiryType,
            inquiry.status,
            inquiry.userId,
            inquiry.content,
            inquiry.assignedAdminId,
            inquiry.answeredByAdminId,
            inquiry.resolvedAt,
            inquiry.createdAt,
        )

    private fun statusEq(status: InquiryStatus?): Predicate? = status?.let { inquiry.status.eq(it) }

    private fun inquiryTypeEq(inquiryType: InquiryType?): Predicate? = inquiryType?.let { inquiry.inquiryType.eq(it) }

    private fun keywordContains(keyword: String?): Predicate? =
        keyword?.let {
            inquiry.content.containsIgnoreCase(it).or(inquiry.replyEmail.containsIgnoreCase(it))
        }

    /**
     * (created_at, id) 복합 키의 이전 지점 — 기준 행보다 앞선 접수만 남긴다.
     * id만으로 자기 행을 빼면 "나중에 들어온 문의"가 과거 이력에 섞여 상한을 차지한다.
     */
    private fun beforeAnchor(
        beforeCreatedAt: LocalDateTime,
        beforeInquiryId: Long,
    ): Predicate =
        inquiry.createdAt
            .lt(beforeCreatedAt)
            .or(inquiry.createdAt.eq(beforeCreatedAt).and(inquiry.id.lt(beforeInquiryId)))

    /** (created_at, id) 복합 키의 다음 지점 — 동시각 행을 건너뛰지 않으려면 id 비교가 함께 필요하다. */
    private fun afterAnchor(anchor: AdminInquiryAnchor?): Predicate? =
        anchor?.let {
            inquiry.createdAt
                .gt(it.lastCreatedAt)
                .or(inquiry.createdAt.eq(it.lastCreatedAt).and(inquiry.id.gt(it.lastInquiryId)))
        }
}
