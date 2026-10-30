package com.aechak.infra.persistence.notice

import com.aechak.application.notice.read.port.NoticeReadAnchor
import com.aechak.application.notice.read.port.NoticeReadCondition
import com.aechak.application.notice.read.port.NoticeReadQueryPort
import com.aechak.application.notice.read.port.view.NoticeReadView
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
 * 독자용 공지 조회를 QueryDSL로 수행한다.
 *
 * 노출 판정(게시 상태 + 독자 대상)은 세 메서드 전부가 같은 [visibleTo] 조건으로 건다 —
 * 호출자가 조건을 빠뜨려 초안이나 다른 대상의 공지를 읽는 경로를 만들지 않으려고 포트 구현에 둔다.
 * 정렬은 (pinned, published_at, id) 내림차순 고정.
 */
@Repository
class NoticeReadQueryAdapter(
    private val queryFactory: JPAQueryFactory,
) : NoticeReadQueryPort {
    override fun findPage(condition: NoticeReadCondition): List<NoticeReadView> =
        filtered(condition, viewProjection())
            .where(afterAnchor(condition.anchor))
            .orderBy(notice.pinned.desc(), notice.publishedAt.desc(), notice.id.desc())
            .limit(condition.limit.toLong())
            .fetch()

    override fun count(condition: NoticeReadCondition): Long = filtered(condition, notice.count()).fetchOne() ?: 0L

    override fun findVisible(
        publicId: String,
        reader: NoticeAudience,
    ): NoticeReadView? =
        queryFactory
            .select(viewProjection())
            .from(notice)
            .where(notice.publicId.eq(publicId), visibleTo(reader))
            .fetchOne()

    private fun <T> filtered(
        condition: NoticeReadCondition,
        select: Expression<T>,
    ): JPAQuery<T> =
        queryFactory
            .select(select)
            .from(notice)
            .where(visibleTo(condition.reader), categoryEq(condition.category))

    /** 게시된 공지 중 그 독자 대상에 해당하는 것 — 전체 대상(ALL)과 자기 대상. */
    private fun visibleTo(reader: NoticeAudience): Predicate =
        notice.status
            .eq(NoticeStatus.PUBLISHED)
            .and(notice.audience.`in`(reader.visibleTo()))

    private fun categoryEq(category: NoticeCategory?): Predicate? = category?.let { notice.category.eq(it) }

    private fun viewProjection(): Expression<NoticeReadView> =
        Projections.constructor(
            NoticeReadView::class.java,
            notice.id,
            notice.publicId,
            notice.title,
            notice.content,
            notice.category,
            notice.pinned,
            notice.publishedAt,
        )

    /**
     * (pinned, published_at, id) 내림차순의 다음 지점.
     *
     * pinned가 불리언이라 `lt`를 쓰지 않고 값으로 갈라 쓴다 — 고정 공지에서 끊겼으면
     * "같은 고정 묶음의 뒤쪽" 또는 "고정이 아닌 전부"가 다음이고, 일반 공지에서 끊겼으면
     * 고정 묶음은 이미 지나갔으므로 일반 공지의 뒤쪽만 남는다.
     */
    private fun afterAnchor(anchor: NoticeReadAnchor?): Predicate? {
        if (anchor == null) {
            return null
        }
        val withinSamePinnedGroup =
            notice.pinned
                .eq(anchor.lastPinned)
                .and(
                    notice.publishedAt
                        .lt(anchor.lastPublishedAt)
                        .or(notice.publishedAt.eq(anchor.lastPublishedAt).and(notice.id.lt(anchor.lastNoticeId))),
                )
        return if (anchor.lastPinned) {
            withinSamePinnedGroup.or(notice.pinned.isFalse)
        } else {
            withinSamePinnedGroup
        }
    }
}
