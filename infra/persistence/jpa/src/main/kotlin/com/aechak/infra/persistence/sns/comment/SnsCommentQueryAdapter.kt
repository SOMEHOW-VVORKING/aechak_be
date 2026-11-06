package com.aechak.infra.persistence.sns.comment

import com.aechak.application.sns.port.SnsCommentQueryPort
import com.aechak.application.sns.port.SnsCommentScope
import com.aechak.application.sns.port.view.SnsCommentView
import com.aechak.domain.sns.comment.QComment
import com.aechak.domain.sns.comment.enums.CommentStatus
import com.aechak.domain.sns.mute.QMute
import com.aechak.domain.user.user.QUser
import com.aechak.domain.user.user.QUserProfile
import com.querydsl.core.types.ExpressionUtils
import com.querydsl.core.types.Predicate
import com.querydsl.core.types.Projections
import com.querydsl.core.types.dsl.BooleanExpression
import com.querydsl.core.types.dsl.NumberPath
import com.querydsl.jpa.JPAExpressions
import com.querydsl.jpa.impl.JPAQuery
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Repository

private val comment = QComment.comment
private val user = QUser.user
private val profile = QUserProfile.userProfile

/**
 * 댓글 X가 보는 사람에게 보이는 조건을 한 곳에서 만든다. R은 X의 최상위 댓글이다(최상위 댓글이면 X 자신).
 * X와 R의 작성자가 보는 사람에게 숨긴 사용자가 아니고, X가 ACTIVE이거나 X가 지운 최상위 댓글이며 그 아래 보이는 ACTIVE 대댓글이 있어야 한다.
 */
@Repository
class SnsCommentQueryAdapter(
    private val queryFactory: JPAQueryFactory,
) : SnsCommentQueryPort {
    override fun findRootPage(
        scope: SnsCommentScope,
        lastRootId: Long?,
        limit: Int,
    ): List<SnsCommentView> =
        selectViews(comment)
            .where(
                comment.post.id.eq(scope.postId),
                comment.parent.isNull,
                lastRootId?.let { comment.id.gt(it) },
                authorVisible(comment.authorId, scope),
                visibleState(comment, scope),
            ).orderBy(comment.id.asc())
            .limit(limit.toLong())
            .fetch()

    // shortcut: 한 페이지가 최상위 댓글의 대댓글을 모두 싣는다. 최상위 댓글 하나의 대댓글이 200개를 넘거나
    // 댓글 목록 응답 P95가 300ms를 넘으면 대댓글을 부모별로 나눠 읽는 엔드포인트를 추가한다
    override fun findReplies(
        scope: SnsCommentScope,
        rootIds: List<Long>,
    ): List<SnsCommentView> {
        if (rootIds.isEmpty()) return emptyList()
        // 최상위 댓글 작성자는 findRootPage가 이미 걸렀다
        return selectViews(comment)
            .where(
                comment.parent.id.`in`(rootIds),
                comment.status.eq(CommentStatus.ACTIVE),
                authorVisible(comment.authorId, scope),
            ).orderBy(comment.parent.id.asc(), comment.id.asc())
            .fetch()
    }

    /** 작성자 표시 정보는 id 동등 LEFT JOIN으로 같은 쿼리에서 읽는다. 프로필 행이 없으면 null이다 */
    private fun selectViews(c: QComment): JPAQuery<SnsCommentView> =
        queryFactory
            .select(
                Projections.constructor(
                    SnsCommentView::class.java,
                    c.id,
                    c.publicId,
                    c.parent.id,
                    c.authorId,
                    user.publicId,
                    profile.nickname,
                    profile.profileImageKey,
                    c.content,
                    c.status,
                    c.deletionReason,
                    c.editedAt,
                    c.createdAt,
                ),
            ).from(c)
            .leftJoin(user)
            .on(user.id.eq(c.authorId))
            .leftJoin(profile)
            .on(profile.userId.eq(c.authorId))

    /** ACTIVE이거나, 지운 최상위 댓글이고 그 아래 보이는 ACTIVE 대댓글이 하나 이상 있다(안내 문구) */
    private fun visibleState(
        c: QComment,
        scope: SnsCommentScope,
    ): BooleanExpression {
        val reply = QComment("visibleReply")
        val hasVisibleReply =
            JPAExpressions
                .selectOne()
                .from(reply)
                .where(reply.parent.id.eq(c.id), reply.status.eq(CommentStatus.ACTIVE), authorVisible(reply.authorId, scope))
                .exists()
        return c.status.eq(CommentStatus.ACTIVE).or(c.parent.isNull.and(hasVisibleReply))
    }

    /** 이 사용자가 쓴 댓글이 보는 사람에게 숨겨지지 않는다. 섀도우 처리, 차단 관계, 보는 사람의 뮤트다 */
    private fun authorVisible(
        authorId: NumberPath<Long>,
        scope: SnsCommentScope,
    ): Predicate? =
        ExpressionUtils.allOf(
            notShadowed(authorId, scope),
            // 빈 집합이면 NOT IN 조건을 빼 거르지 않는다
            scope.hidden.takeIf { it.isNotEmpty() }?.let { authorId.notIn(it) },
            scope.muted.takeIf { it.isNotEmpty() }?.let { authorId.notIn(it) },
        )

    /**
     * 게시물 작성자가 뮤트한 사용자(섀도우 처리된 사용자)의 댓글은 그 사용자 본인에게만 보인다.
     * 댓글을 쓴 시점과 상관없이 지금의 뮤트 관계로 정한다. uk_sns_mute_pair로 찾는다.
     */
    private fun notShadowed(
        authorId: NumberPath<Long>,
        scope: SnsCommentScope,
    ): BooleanExpression {
        // 한 쿼리에서 댓글과 그 대댓글에 함께 걸리므로 댓글 별칭마다 다른 별칭을 쓴다
        val mute = QMute("shadowOf${authorId.metadata.parent!!.metadata.name}")
        val notMuted =
            JPAExpressions
                .selectOne()
                .from(mute)
                .where(mute.muterId.eq(scope.postAuthorId), mute.mutedId.eq(authorId))
                .notExists()
        return scope.viewerId?.let { notMuted.or(authorId.eq(it)) } ?: notMuted
    }
}
