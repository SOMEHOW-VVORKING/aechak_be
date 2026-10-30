package com.aechak.infra.persistence.notice

import com.aechak.application.notice.admin.port.AdminNoticeAnchor
import com.aechak.application.notice.admin.port.AdminNoticeCondition
import com.aechak.application.notice.admin.port.AdminNoticeQueryPort
import com.aechak.application.notice.admin.port.view.AdminNoticeView
import com.aechak.domain.notice.notice.QNotice
import com.aechak.domain.notice.notice.enums.NoticeAudience
import com.aechak.domain.notice.notice.enums.NoticeCategory
import com.aechak.domain.notice.notice.enums.NoticeStatus
import com.querydsl.core.types.Expression
import com.querydsl.core.types.Predicate
import com.querydsl.core.types.Projections
import com.querydsl.jpa.impl.JPAQuery
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Repository

private val notice = QNotice.notice

/**
 * 운영자용 공지 목록 조회를 QueryDSL로 수행한다.
 * 정렬은 최근 작성순(created_at, id) 내림차순 고정 — 방금 쓴 공지가 맨 위에 보여야 한다.
 * 키워드는 제목만 훑는다(운영 화면의 검색창이 제목 검색이다).
 */
@Repository
class AdminNoticeQueryAdapter(
    private val queryFactory: JPAQueryFactory,
) : AdminNoticeQueryPort {
    override fun findPage(condition: AdminNoticeCondition): List<AdminNoticeView> =
        filtered(condition, viewProjection())
            .where(afterAnchor(condition.anchor))
            .orderBy(notice.createdAt.desc(), notice.id.desc())
            .limit(condition.limit.toLong())
            .fetch()

    override fun count(condition: AdminNoticeCondition): Long = filtered(condition, notice.count()).fetchOne() ?: 0L

    /** 목록·카운트가 공유하는 필터 골격. 커서 앵커는 목록에만 걸린다 — 총건수는 페이지와 무관하다. */
    private fun <T> filtered(
        condition: AdminNoticeCondition,
        select: Expression<T>,
    ): JPAQuery<T> =
        queryFactory
            .select(select)
            .from(notice)
            .where(
                statusEq(condition.status),
                audienceEq(condition.audience),
                categoryEq(condition.category),
                titleContains(condition.keyword),
            )

    private fun viewProjection(): Expression<AdminNoticeView> =
        Projections.constructor(
            AdminNoticeView::class.java,
            notice.id,
            notice.publicId,
            notice.title,
            notice.category,
            notice.audience,
            notice.status,
            notice.pinned,
            notice.publishedAt,
            notice.lastModifiedByAdminId,
            notice.createdAt,
        )

    private fun statusEq(status: NoticeStatus?): Predicate? = status?.let { notice.status.eq(it) }

    private fun audienceEq(audience: NoticeAudience?): Predicate? = audience?.let { notice.audience.eq(it) }

    private fun categoryEq(category: NoticeCategory?): Predicate? = category?.let { notice.category.eq(it) }

    private fun titleContains(keyword: String?): Predicate? = keyword?.let { notice.title.containsIgnoreCase(it) }

    /** (created_at, id) 복합 키의 다음 지점 — 동시각 행을 건너뛰지 않으려면 id 비교가 함께 필요하다. */
    private fun afterAnchor(anchor: AdminNoticeAnchor?): Predicate? =
        anchor?.let {
            notice.createdAt
                .lt(it.lastCreatedAt)
                .or(notice.createdAt.eq(it.lastCreatedAt).and(notice.id.lt(it.lastNoticeId)))
        }
}
