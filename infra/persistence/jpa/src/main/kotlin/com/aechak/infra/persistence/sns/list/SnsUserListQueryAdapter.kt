package com.aechak.infra.persistence.sns.list

import com.aechak.application.sns.port.SnsFollowDirection
import com.aechak.application.sns.port.SnsRelationKind
import com.aechak.application.sns.port.SnsUserListQueryPort
import com.aechak.application.sns.port.view.SnsFollowUserView
import com.aechak.application.sns.port.view.SnsRelationUserView
import com.aechak.domain.sns.block.QBlock
import com.aechak.domain.sns.follow.QFollow
import com.aechak.domain.sns.mute.QMute
import com.aechak.domain.user.user.QUser
import com.aechak.domain.user.user.QUserProfile
import com.querydsl.core.types.EntityPath
import com.querydsl.core.types.Projections
import com.querydsl.core.types.dsl.DateTimePath
import com.querydsl.core.types.dsl.NumberPath
import com.querydsl.jpa.impl.JPAQuery
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

private val block = QBlock.block
private val follow = QFollow.follow
private val mute = QMute.mute
private val user = QUser.user
private val profile = QUserProfile.userProfile

/** 관계 테이블의 컬럼 묶음. 차단과 뮤트가 같은 쿼리를 쓴다 */
private class RelationTable(
    val entity: EntityPath<*>,
    val id: NumberPath<Long>,
    val ownerId: NumberPath<Long>,
    val targetId: NumberPath<Long>,
    val createdAt: DateTimePath<LocalDateTime>,
)

// LIKE 이스케이프 문자. 쿼리에 명시해 DB 기본값에 기대지 않는다
private const val LIKE_ESCAPE = '\\'

/** 검색어를 글자 그대로 부분 일치하는 LIKE 패턴으로 바꾼다. 이스케이프 문자를 먼저 이스케이프한다 */
internal fun nicknameContainsPattern(nickname: String): String =
    "%" + nickname.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"

private fun SnsRelationKind.table(): RelationTable =
    when (this) {
        SnsRelationKind.BLOCK -> RelationTable(block, block.id, block.blockerId, block.blockedId, block.createdAt)
        SnsRelationKind.MUTE -> RelationTable(mute, mute.id, mute.muterId, mute.mutedId, mute.createdAt)
    }

/** user BC의 users, user_profiles를 id 동등 조건으로 읽기만 하는 조인. 프로필 행이 없는 탈퇴 사용자는 빠진다 */
@Repository
class SnsUserListQueryAdapter(
    private val queryFactory: JPAQueryFactory,
) : SnsUserListQueryPort {
    override fun findRelationPage(
        ownerId: Long,
        kind: SnsRelationKind,
        lastRelationId: Long?,
        limit: Int,
    ): List<SnsRelationUserView> {
        val t = kind.table()
        return joinedRelations(queryFactory.select(relationProjection(t)), t, ownerId)
            .where(lastRelationId?.let { t.id.lt(it) })
            .orderBy(t.id.desc())
            .limit(limit.toLong())
            .fetch()
    }

    override fun countRelations(
        ownerId: Long,
        kind: SnsRelationKind,
    ): Long {
        val t = kind.table()
        return joinedRelations(queryFactory.select(t.id.count()), t, ownerId).fetchOne() ?: 0L
    }

    // shortcut: 앞에 와일드카드가 붙은 LIKE라 인덱스를 못 쓰고 맞는 행이 적으면 주인의 팔로우 행을 끝까지 읽는다.
    // 팔로워 1만 명 이상 계정이 생기거나 목록 응답 시간이 나빠지면 요청당 읽는 행에 상한을 둔다.
    override fun findFollowPage(
        ownerId: Long,
        direction: SnsFollowDirection,
        hiddenUserIds: Set<Long>,
        nickname: String?,
        lastFollowId: Long?,
        limit: Int,
    ): List<SnsFollowUserView> {
        val (ownerColumn, rowUserColumn) =
            when (direction) {
                SnsFollowDirection.FOLLOWERS -> follow.followeeId to follow.followerId
                SnsFollowDirection.FOLLOWINGS -> follow.followerId to follow.followeeId
            }
        return queryFactory
            .select(
                Projections.constructor(
                    SnsFollowUserView::class.java,
                    follow.id,
                    user.id,
                    user.publicId,
                    profile.nickname,
                    profile.profileImageKey,
                ),
            ).from(follow)
            .join(user)
            .on(user.id.eq(rowUserColumn))
            .join(profile)
            .on(profile.userId.eq(user.id))
            .where(
                ownerColumn.eq(ownerId),
                lastFollowId?.let { follow.id.lt(it) },
                // 빈 집합이면 NOT IN 조건을 빼 거르지 않는다
                hiddenUserIds.takeIf { it.isNotEmpty() }?.let { rowUserColumn.notIn(it) },
                nickname?.let { profile.nickname.like(nicknameContainsPattern(it), LIKE_ESCAPE) },
            ).orderBy(follow.id.desc())
            .limit(limit.toLong())
            .fetch()
    }

    private fun <T> joinedRelations(
        query: JPAQuery<T>,
        t: RelationTable,
        ownerId: Long,
    ): JPAQuery<T> =
        query
            .from(t.entity)
            .join(user)
            .on(user.id.eq(t.targetId))
            .join(profile)
            .on(profile.userId.eq(user.id))
            .where(t.ownerId.eq(ownerId))

    private fun relationProjection(t: RelationTable) =
        Projections.constructor(
            SnsRelationUserView::class.java,
            t.id,
            user.publicId,
            profile.nickname,
            profile.profileImageKey,
            t.createdAt,
        )
}
