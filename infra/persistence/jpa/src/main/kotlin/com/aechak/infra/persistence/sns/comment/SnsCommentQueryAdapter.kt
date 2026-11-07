package com.aechak.infra.persistence.sns.comment

import com.aechak.application.sns.port.SnsCommentQueryPort
import com.aechak.application.sns.port.SnsCommentScope
import com.aechak.application.sns.port.view.SnsCommentRefView
import com.aechak.application.sns.port.view.SnsCommentView
import com.aechak.domain.sns.comment.QComment
import com.aechak.domain.sns.comment.enums.CommentStatus
import com.aechak.domain.sns.mute.QMute
import com.aechak.domain.sns.post.QPost
import com.aechak.domain.user.user.QUser
import com.aechak.domain.user.user.QUserProfile
import com.aechak.domain.user.user.enums.UserStatus
import com.querydsl.core.types.ExpressionUtils
import com.querydsl.core.types.Predicate
import com.querydsl.core.types.Projections
import com.querydsl.core.types.dsl.BooleanExpression
import com.querydsl.core.types.dsl.Expressions
import com.querydsl.core.types.dsl.NumberExpression
import com.querydsl.core.types.dsl.NumberPath
import com.querydsl.jpa.JPAExpressions
import com.querydsl.jpa.JPQLSubQuery
import com.querydsl.jpa.impl.JPAQuery
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Repository

private val comment = QComment.comment
private val user = QUser.user
private val profile = QUserProfile.userProfile

/**
 * 댓글 X가 보는 사람에게 보이는 조건을 한 곳에서 만든다. R은 X의 최상위 댓글이다(최상위 댓글이면 X 자신).
 * X와 R의 작성자가 보는 사람에게 숨긴 사용자가 아니고, X가 ACTIVE이거나 X가 지운 최상위 댓글이며 그 아래 보이는 ACTIVE 대댓글이 있어야 한다.
 * 영구 정지 작성자의 댓글은 지운 댓글처럼 다룬다. 사용자 표를 읽지 않는 findVisibleRef만 이 조건을 빼고 호출한 쪽이 거른다.
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
                visibleState(comment, scope, excludeBanned = true),
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
                notBanned(comment.authorId),
                authorVisible(comment.authorId, scope),
            ).orderBy(comment.parent.id.asc(), comment.id.asc())
            .fetch()
    }

    override fun findVisibleRef(
        scope: SnsCommentScope,
        commentPublicId: String,
    ): SnsCommentRefView? {
        val root = QComment("refRoot")
        return queryFactory
            .select(Projections.constructor(SnsCommentRefView::class.java, comment.id, comment.parent.id, comment.authorId, comment.status))
            .from(comment)
            .leftJoin(comment.parent, root)
            .where(comment.publicId.eq(commentPublicId), visibleWithRoot(root, scope, excludeBanned = false))
            .fetchOne()
    }

    override fun findVisible(
        scope: SnsCommentScope,
        commentId: Long,
    ): SnsCommentView? {
        val root = QComment("visibleRoot")
        return selectViews(comment)
            .leftJoin(comment.parent, root)
            .where(comment.id.eq(commentId), visibleWithRoot(root, scope, excludeBanned = true))
            .fetchOne()
    }

    /** 이 게시물의 댓글이 보는 사람에게 보인다. root는 comment.parent를 LEFT JOIN한 별칭이다 */
    private fun visibleWithRoot(
        root: QComment,
        scope: SnsCommentScope,
        excludeBanned: Boolean,
    ): Predicate? =
        ExpressionUtils.allOf(
            comment.post.id.eq(scope.postId),
            authorVisible(comment.authorId, scope),
            comment.parent.isNull.or(authorVisible(root.authorId, scope)),
            visibleState(comment, scope, excludeBanned),
        )

    // shortcut: 상세와 카드마다 댓글 행을 센다. 행마다 sns_mutes 고유 인덱스를 두 번 찾고 최상위 댓글 작성자와 댓글 작성자의 users 행을 기본 키로 한 번씩 읽는다.
    // 게시물 하나의 댓글이 1만 개를 넘거나 상세 P95가 200ms, 피드 P95가 300ms를 넘으면
    // 공개 수를 게시물 통계 행에 저장하고 섀도우 처리된 작성자의 댓글만 볼 때 빼는 방식으로 바꾼다
    override fun countVisible(
        postIds: Collection<Long>,
        viewerId: Long?,
    ): Map<Long, Long> {
        if (postIds.isEmpty()) return emptyMap()
        val post = QPost("countedPost")
        val root = QComment("countedRoot")
        // 차단과 일반 뮤트는 보는 사람에게만 숨기므로 수를 줄이지 않는다. 섀도우 규칙, ACTIVE, 영구 정지만 쓴다
        val rows =
            queryFactory
                .select(comment.post.id, comment.id.count())
                .from(comment)
                .join(comment.post, post)
                .leftJoin(comment.parent, root)
                .where(
                    comment.post.id.`in`(postIds),
                    comment.status.eq(CommentStatus.ACTIVE),
                    notBanned(comment.authorId),
                    notShadowed(comment.authorId, post.authorId, viewerId),
                    comment.parent.isNull.or(notShadowed(root.authorId, post.authorId, viewerId)),
                ).groupBy(comment.post.id)
                .fetch()
        val counts = rows.associate { it.get(0, Long::class.java)!! to it.get(1, Long::class.java)!! }
        return postIds.associateWith { counts[it] ?: 0L }
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
                    user.status,
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

    /**
     * ACTIVE이거나, 지운 최상위 댓글이고 그 아래 보이는 ACTIVE 대댓글이 하나 이상 있다(안내 문구).
     * excludeBanned면 영구 정지 작성자의 댓글을 지운 댓글처럼 다룬다
     */
    private fun visibleState(
        c: QComment,
        scope: SnsCommentScope,
        excludeBanned: Boolean,
    ): BooleanExpression {
        val reply = QComment("visibleReply")
        val hasVisibleReply =
            JPAExpressions
                .selectOne()
                .from(reply)
                .where(
                    reply.parent.id.eq(c.id),
                    reply.status.eq(CommentStatus.ACTIVE),
                    if (excludeBanned) notBanned(reply.authorId) else null,
                    authorVisible(reply.authorId, scope),
                ).exists()
        val live = c.status.eq(CommentStatus.ACTIVE).let { if (excludeBanned) it.and(notBanned(c.authorId)) else it }
        return live.or(c.parent.isNull.and(hasVisibleReply))
    }

    /** 이 사용자가 쓴 댓글이 보는 사람에게 숨겨지지 않는다. 섀도우 처리, 차단 관계, 보는 사람의 뮤트다 */
    private fun authorVisible(
        authorId: NumberPath<Long>,
        scope: SnsCommentScope,
    ): Predicate? =
        ExpressionUtils.allOf(
            notShadowed(authorId, Expressions.asNumber(scope.postAuthorId), scope.viewerId),
            // 빈 집합이면 NOT IN 조건을 빼 거르지 않는다
            scope.hidden.takeIf { it.isNotEmpty() }?.let { authorId.notIn(it) },
            scope.muted.takeIf { it.isNotEmpty() }?.let { authorId.notIn(it) },
        )
}

/** 작성자가 영구 정지 상태가 아니다. 한 쿼리의 댓글 별칭마다 다른 사용자 별칭을 쓴다 */
private fun notBanned(authorId: NumberPath<Long>): BooleanExpression {
    val banned = QUser("bannedOf${authorId.metadata.parent!!.metadata.name}")
    return JPAExpressions
        .selectOne()
        .from(banned)
        .where(banned.id.eq(authorId), banned.status.eq(UserStatus.BANNED))
        .notExists()
}

/**
 * 게시물 작성자가 뮤트한 사용자(섀도우 처리된 사용자)의 댓글은 그 사용자 본인에게만 보인다.
 * 댓글을 쓴 시점과 상관없이 지금의 뮤트 관계로 정한다. uk_sns_mute_pair로 찾는다.
 */
private fun notShadowed(
    authorId: NumberPath<Long>,
    postAuthorId: NumberExpression<Long>,
    viewerId: Long?,
): BooleanExpression {
    // 한 쿼리에서 댓글과 그 대댓글에 함께 걸리므로 댓글 별칭마다 다른 별칭을 쓴다
    val mute = QMute("shadowOf${authorId.metadata.parent!!.metadata.name}")
    val notMuted =
        JPAExpressions
            .selectOne()
            .from(mute)
            .where(mute.muterId.eq(postAuthorId), mute.mutedId.eq(authorId))
            .notExists()
    return viewerId?.let { notMuted.or(authorId.eq(it)) } ?: notMuted
}

/**
 * 게시물의 공개 댓글 수. 비로그인 카드의 수와 같이 ACTIVE 댓글과 대댓글을 섀도우 규칙으로만 거르고 작성자 본인 댓글도 센다.
 * 인기 점수 쿼리가 게시물마다 부르는 상관 서브쿼리다.
 */
internal fun publicCommentCount(
    postId: NumberExpression<Long>,
    postAuthorId: NumberExpression<Long>,
): JPQLSubQuery<Long> {
    val scored = QComment("scoredComment")
    val root = QComment("scoredRoot")
    return JPAExpressions
        .select(scored.count())
        .from(scored)
        .leftJoin(scored.parent, root)
        .where(
            scored.post.id.eq(postId),
            scored.status.eq(CommentStatus.ACTIVE),
            notShadowed(scored.authorId, postAuthorId, null),
            scored.parent.isNull.or(notShadowed(root.authorId, postAuthorId, null)),
        )
}
