package com.aechak.infra.persistence.audit

import com.aechak.application.audit.port.AdminAuditLogCondition
import com.aechak.application.audit.port.AdminAuditTrailQueryPort
import com.aechak.application.audit.port.view.AdminAuditLogRowView
import com.aechak.domain.audit.auditlog.QAdminAuditLog
import com.aechak.domain.user.user.QUser
import com.aechak.domain.user.user.QUserProfile
import com.querydsl.core.types.Predicate
import com.querydsl.core.types.Projections
import com.querydsl.jpa.impl.JPAQuery
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

private val auditLog = QAdminAuditLog.adminAuditLog
private val actor = QUser.user
private val actorProfile = QUserProfile.userProfile

// LIKE 이스케이프 문자. 쿼리에 명시해 DB 기본값에 기대지 않는다
private const val LIKE_ESCAPE = '\\'

/** 검색어를 글자 그대로 부분 일치하는 LIKE 패턴으로 바꾼다. 이스케이프 문자를 먼저 이스케이프한다 */
internal fun auditKeywordPattern(keyword: String): String =
    "%" + keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"

/**
 * 감사 로그 조회. 조치한 운영자의 신원은 user BC의 users·user_profiles를 **LEFT JOIN**으로 붙인다 —
 * 조치한 계정이 지워져도 그 조치의 행은 사라지면 안 된다(그게 사라지면 감사 로그가 아니다).
 *
 * 정렬과 커서는 id 하나다 — 기록 순서로 내려 읽는다. acted_at 은 INSERT 전에 요청 스레드가
 * 찍으므로 동시 조치에서 id 순서와 밀리초 단위로 어긋날 수 있다(그 경우 목록의 시각이 아주
 * 조금 뒤바뀌어 보인다). acted_at 정렬로 바꾸면 늦게 INSERT 된 행이 이미 지나간 시각 자리로
 * 들어가 **읽는 중에 건너뛰어진다** — 감사 로그에서는 그게 더 나쁜 고장이다.
 */
@Repository
class AdminAuditTrailQueryAdapter(
    private val queryFactory: JPAQueryFactory,
) : AdminAuditTrailQueryPort {
    override fun findPage(
        condition: AdminAuditLogCondition,
        lastId: Long?,
        limit: Int,
    ): List<AdminAuditLogRowView> =
        filtered(queryFactory.select(rowProjection()), condition)
            .where(lastId?.let { auditLog.id.lt(it) })
            .orderBy(auditLog.id.desc())
            .limit(limit.toLong())
            .fetch()

    override fun count(condition: AdminAuditLogCondition): Long =
        filtered(queryFactory.select(auditLog.id.count()), condition).fetchOne() ?: 0L

    /** 조건을 붙이지 않는다 — 기록 시작 시점은 필터와 무관한 테이블 전체의 사실이다 */
    override fun findRecordingSince(): LocalDateTime? = queryFactory.select(auditLog.actedAt.min()).from(auditLog).fetchOne()

    private fun <T> filtered(
        query: JPAQuery<T>,
        condition: AdminAuditLogCondition,
    ): JPAQuery<T> =
        query
            .from(auditLog)
            .leftJoin(actor)
            .on(actor.id.eq(auditLog.actorUserId))
            .leftJoin(actorProfile)
            .on(actorProfile.userId.eq(auditLog.actorUserId))
            .where(
                condition.actorPublicId?.let { actor.publicId.eq(it) },
                condition.targetType?.let { auditLog.targetType.eq(it) },
                condition.action?.let { auditLog.action.eq(it) },
                condition.result?.let { auditLog.result.eq(it) },
                condition.from?.let { auditLog.actedAt.goe(it) },
                condition.to?.let { auditLog.actedAt.loe(it) },
                condition.keyword?.let { keywordMatches(it) },
            )

    /** 대상 이름·대상 식별자·사유 중 하나라도 맞으면 남긴다 */
    private fun keywordMatches(keyword: String): Predicate {
        val pattern = auditKeywordPattern(keyword)
        return auditLog.targetLabel
            .like(pattern, LIKE_ESCAPE)
            .or(auditLog.targetId.like(pattern, LIKE_ESCAPE))
            .or(auditLog.reason.like(pattern, LIKE_ESCAPE))
    }

    private fun rowProjection() =
        Projections.constructor(
            AdminAuditLogRowView::class.java,
            auditLog.id,
            auditLog.publicId,
            auditLog.actedAt,
            auditLog.actorUserId,
            actor.publicId,
            actorProfile.nickname,
            actor.role,
            auditLog.action,
            auditLog.targetType,
            auditLog.targetId,
            auditLog.targetLabel,
            auditLog.reason,
            auditLog.result,
            auditLog.httpStatus,
            auditLog.requestId,
            auditLog.clientIp,
        )
}
