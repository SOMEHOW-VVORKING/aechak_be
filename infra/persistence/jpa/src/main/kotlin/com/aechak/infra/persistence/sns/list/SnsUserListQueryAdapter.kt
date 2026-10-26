package com.aechak.infra.persistence.sns.list

import com.aechak.application.sns.port.SnsRelationKind
import com.aechak.application.sns.port.SnsUserListQueryPort
import com.aechak.application.sns.port.view.SnsRelationUserView
import com.aechak.domain.sns.block.QBlock
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
