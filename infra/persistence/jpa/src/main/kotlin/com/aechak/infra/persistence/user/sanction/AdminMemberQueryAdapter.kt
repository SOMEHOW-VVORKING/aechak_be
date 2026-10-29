package com.aechak.infra.persistence.user.sanction

import com.aechak.application.user.sanction.port.AdminMemberQueryPort
import com.aechak.application.user.sanction.port.MemberPageCondition
import com.aechak.application.user.sanction.port.view.MemberSummaryView
import com.aechak.application.user.sanction.port.view.SanctionHistoryView
import com.aechak.domain.user.report.QUserReport
import com.aechak.domain.user.sanction.QUserSanction
import com.aechak.domain.user.user.QUser
import com.aechak.domain.user.user.QUserProfile
import com.aechak.domain.user.user.enums.UserRole
import com.aechak.domain.user.user.enums.UserStatus
import com.querydsl.core.types.Expression
import com.querydsl.core.types.Predicate
import com.querydsl.core.types.Projections
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

private val user = QUser.user
private val profile = QUserProfile.userProfile
private val report = QUserReport.userReport
private val sanction = QUserSanction.userSanction

/**
 * 어드민 회원 조회 포트의 QueryDSL 어댑터.
 * 닉네임은 user_profiles 에 있어 left join 으로 붙인다 — 온보딩 전 계정도 목록에서 빠지면 안 된다.
 */
@Repository
class AdminMemberQueryAdapter(
    private val queryFactory: JPAQueryFactory,
) : AdminMemberQueryPort {
    override fun findMemberPage(condition: MemberPageCondition): List<MemberSummaryView> =
        withReportCounts(
            queryFactory
                .select(memberRowProjection())
                .from(user)
                .leftJoin(profile)
                .on(profile.userId.eq(user.id))
                .where(
                    generalMembersOnly(),
                    statusIn(condition),
                    keywordMatches(condition),
                    joinedAtRange(condition),
                    keyset(condition),
                ).orderBy(user.id.desc())
                .limit(condition.limit.toLong())
                .fetch(),
        )

    override fun countMembers(condition: MemberPageCondition): Long =
        queryFactory
            .select(user.count())
            .from(user)
            .leftJoin(profile)
            .on(profile.userId.eq(user.id))
            .where(
                generalMembersOnly(),
                statusIn(condition),
                keywordMatches(condition),
                joinedAtRange(condition),
            ).fetchOne() ?: 0L

    override fun findMember(userId: Long): MemberSummaryView? =
        withReportCounts(
            queryFactory
                .select(memberRowProjection())
                .from(user)
                .leftJoin(profile)
                .on(profile.userId.eq(user.id))
                .where(user.id.eq(userId), generalMembersOnly())
                .fetch(),
        ).firstOrNull()

    override fun findSanctionHistory(
        userId: Long,
        limit: Int,
    ): List<SanctionHistoryView> =
        queryFactory
            .select(
                Projections.constructor(
                    SanctionHistoryView::class.java,
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
            .where(sanction.targetUser.id.eq(userId))
            .orderBy(sanction.id.desc())
            .limit(limit.toLong())
            .fetch()

    /**
     * 신고 누적 수를 한 페이지 분량만 묶어 한 번 더 조회해 붙인다.
     * 상관 서브쿼리로 투영하지 않는 이유: 행마다 1회 실행인데다 QueryDSL 투영에서 타입이 지워져
     * 생성자 매칭이 깨진다. 페이지가 정해진 뒤 id 목록으로 묻는 편이 쿼리 2회로 끝난다.
     */
    private fun withReportCounts(rows: List<MemberRowProjection>): List<MemberSummaryView> {
        if (rows.isEmpty()) return emptyList()
        val counts = reportCountsOf(rows.map { it.userId })
        return rows.map {
            MemberSummaryView(
                userId = it.userId,
                publicId = it.publicId,
                nickname = it.nickname,
                status = it.status,
                sanctionUntil = it.sanctionUntil,
                warningCount = it.warningCount,
                reportCount = counts[it.userId] ?: 0L,
                joinedAt = it.joinedAt,
            )
        }
    }

    private fun reportCountsOf(userIds: List<Long>): Map<Long, Long> =
        queryFactory
            .select(report.targetUser.id, report.count())
            .from(report)
            .where(report.targetUser.id.`in`(userIds))
            .groupBy(report.targetUser.id)
            .fetch()
            .mapNotNull { tuple ->
                val id = tuple.get(report.targetUser.id) ?: return@mapNotNull null
                id to (tuple.get(report.count()) ?: 0L)
            }.toMap()

    /** 관리자 계정은 관리자 명부(/admin/admins)가 다룬다 — 이 목록은 일반 회원 전용이다. */
    private fun generalMembersOnly(): Predicate = user.role.eq(UserRole.GENERAL)

    private fun statusIn(condition: MemberPageCondition): Predicate? =
        condition.statuses.takeIf { it.isNotEmpty() }?.let { user.status.`in`(it) }

    /** 운영자가 검색창 한 칸에 회원 id 를 넣어도 닉네임을 넣어도 찾히게 둘을 OR 로 묶는다. */
    private fun keywordMatches(condition: MemberPageCondition): Predicate? {
        val nickname = condition.nicknameKeyword ?: return null
        val byNickname = profile.nickname.contains(nickname)
        val userId = condition.userIdKeyword ?: return byNickname
        return byNickname.or(user.id.eq(userId))
    }

    private fun joinedAtRange(condition: MemberPageCondition): Predicate? {
        val from = condition.joinedFrom?.let { user.createdAt.goe(it) }
        val to = condition.joinedToExclusive?.let { user.createdAt.lt(it) }
        return when {
            from != null && to != null -> from.and(to)
            else -> from ?: to
        }
    }

    private fun keyset(condition: MemberPageCondition): Predicate? = condition.lastUserId?.let { user.id.lt(it) }

    private fun memberRowProjection(): Expression<MemberRowProjection> =
        Projections.constructor(
            MemberRowProjection::class.java,
            user.id,
            user.publicId,
            profile.nickname,
            user.status,
            user.sanctionUntil,
            user.warningCount,
            user.createdAt,
        )
}

/** 어댑터 내부 전용 — 신고 수를 채우기 전의 한 행. */
data class MemberRowProjection(
    val userId: Long,
    val publicId: String,
    val nickname: String?,
    val status: UserStatus,
    val sanctionUntil: LocalDateTime?,
    val warningCount: Int,
    val joinedAt: LocalDateTime,
)
