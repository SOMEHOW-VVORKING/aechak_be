package com.aechak.infra.persistence.sns.feed

import com.aechak.application.sns.port.SnsFeedQueryPort
import com.aechak.application.sns.port.view.SnsFeedEntryView
import com.aechak.application.sns.port.view.SnsPostCardView
import com.aechak.application.sns.port.view.SnsPostHashtagView
import com.aechak.application.sns.port.view.SnsPostMediaView
import com.aechak.domain.sns.follow.QFollow
import com.aechak.domain.sns.hashtag.QHashtag
import com.aechak.domain.sns.post.QPost
import com.aechak.domain.sns.post.QPostHashtag
import com.aechak.domain.sns.post.QPostMedia
import com.aechak.domain.sns.post.enums.PostStatus
import com.aechak.domain.sns.stats.QSnsPostStats
import com.aechak.domain.user.user.QUser
import com.aechak.domain.user.user.QUserProfile
import com.querydsl.core.types.Projections
import com.querydsl.core.types.dsl.BooleanExpression
import com.querydsl.core.types.dsl.NumberExpression
import com.querydsl.jpa.JPAExpressions
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

private val post = QPost.post
private val media = QPostMedia.postMedia
private val postHashtag = QPostHashtag.postHashtag
private val hashtag = QHashtag.hashtag
private val stats = QSnsPostStats.snsPostStats
private val follow = QFollow.follow
private val user = QUser.user
private val profile = QUserProfile.userProfile

/** 볼 수 있는 사용자. 프로필 행이 없는 탈퇴, 온보딩 전 사용자는 빠진다 */
private fun visibleUser(userId: NumberExpression<Long>): BooleanExpression =
    JPAExpressions
        .selectOne()
        .from(profile)
        .where(profile.userId.eq(userId))
        .exists()

/** user BC의 users, user_profiles를 id 동등 조건으로 읽기만 한다. 숨길 작성자를 SQL에서 빼야 keyset 페이지가 크기만큼 찬다 */
@Repository
class SnsFeedQueryAdapter(
    private val queryFactory: JPAQueryFactory,
) : SnsFeedQueryPort {
    override fun findExplorePage(
        viewerId: Long?,
        hiddenUserIds: Set<Long>,
        from: LocalDateTime,
        anchor: LocalDateTime,
        lastCreatedAt: LocalDateTime?,
        lastPostId: Long?,
        limit: Int,
    ): List<SnsFeedEntryView> =
        queryFactory
            .select(Projections.constructor(SnsFeedEntryView::class.java, post.id, post.publicId, post.createdAt))
            .from(post)
            .where(
                post.status.eq(PostStatus.PUBLISHED),
                post.createdAt.between(from, anchor),
                visibleUser(post.authorId),
                // 빈 집합이면 NOT IN 조건을 빼 거르지 않는다
                hiddenUserIds.takeIf { it.isNotEmpty() }?.let { post.authorId.notIn(it) },
                viewerId?.let {
                    post.authorId.ne(it).and(
                        post.authorId.notIn(
                            JPAExpressions
                                .select(follow.followeeId)
                                .from(follow)
                                .where(follow.followerId.eq(it)),
                        ),
                    )
                },
                lastCreatedAt?.let {
                    post.createdAt.lt(it).or(post.createdAt.eq(it).and(post.id.lt(lastPostId)))
                },
            ).orderBy(post.createdAt.desc(), post.id.desc())
            .limit(limit.toLong())
            .fetch()

    override fun findPopularRanking(
        from: LocalDateTime,
        to: LocalDateTime,
        repostWeight: Int,
        limit: Int,
    ): List<Long> {
        val score = stats.repostCount.multiply(repostWeight)
        return queryFactory
            .select(post.id)
            .from(post)
            .join(stats)
            .on(stats.postId.eq(post.id))
            .where(
                post.status.eq(PostStatus.PUBLISHED),
                post.createdAt.between(from, to),
                visibleUser(post.authorId),
                score.gt(0),
            ).orderBy(score.desc(), post.createdAt.desc(), post.id.desc())
            .limit(limit.toLong())
            .fetch()
    }

    override fun findVisiblePostIds(
        postIds: Collection<Long>,
        hiddenUserIds: Set<Long>,
    ): Set<Long> =
        queryFactory
            .select(post.id)
            .from(post)
            .where(
                post.id.`in`(postIds),
                post.status.eq(PostStatus.PUBLISHED),
                visibleUser(post.authorId),
                hiddenUserIds.takeIf { it.isNotEmpty() }?.let { post.authorId.notIn(it) },
            ).fetch()
            .toSet()

    override fun findCards(postIds: Collection<Long>): List<SnsPostCardView> =
        queryFactory
            .select(
                Projections.constructor(
                    SnsPostCardView::class.java,
                    post.id,
                    post.publicId,
                    post.authorId,
                    user.publicId,
                    profile.nickname,
                    profile.profileImageKey,
                    post.petId,
                    post.caption,
                    post.editedAt,
                    post.createdAt,
                    stats.repostCount.coalesce(0),
                ),
            ).from(post)
            .join(user)
            .on(user.id.eq(post.authorId))
            .join(profile)
            .on(profile.userId.eq(post.authorId))
            .leftJoin(stats)
            .on(stats.postId.eq(post.id))
            .where(post.id.`in`(postIds))
            .fetch()

    override fun findMedia(postIds: Collection<Long>): List<SnsPostMediaView> =
        queryFactory
            .select(
                Projections.constructor(
                    SnsPostMediaView::class.java,
                    post.id,
                    media.mediaKind,
                    media.storageKey,
                    media.thumbnailKey,
                    media.durationMs,
                ),
            ).from(post)
            .join(post._media, media)
            .where(post.id.`in`(postIds))
            .orderBy(media.sortOrder.asc())
            .fetch()

    override fun findHashtagNames(postIds: Collection<Long>): List<SnsPostHashtagView> =
        queryFactory
            .select(Projections.constructor(SnsPostHashtagView::class.java, post.id, hashtag.name))
            .from(post)
            .join(post._hashtags, postHashtag)
            .join(hashtag)
            .on(hashtag.id.eq(postHashtag.hashtagId))
            .where(post.id.`in`(postIds))
            .orderBy(postHashtag.sortOrder.asc())
            .fetch()
}
