package com.aechak.infra.persistence.sns.feed

import com.aechak.application.sns.port.SnsFeedQueryPort
import com.aechak.application.sns.port.SnsPostTab
import com.aechak.application.sns.port.view.SnsFeedEntryView
import com.aechak.application.sns.port.view.SnsFeedRepostView
import com.aechak.application.sns.port.view.SnsPostCardView
import com.aechak.application.sns.port.view.SnsPostHashtagView
import com.aechak.application.sns.port.view.SnsPostMediaView
import com.aechak.domain.sns.follow.QFollow
import com.aechak.domain.sns.hashtag.QHashtag
import com.aechak.domain.sns.post.QPost
import com.aechak.domain.sns.post.QPostHashtag
import com.aechak.domain.sns.post.QPostMedia
import com.aechak.domain.sns.post.enums.PostMediaType
import com.aechak.domain.sns.post.enums.PostStatus
import com.aechak.domain.sns.stats.QSnsPostStats
import com.aechak.domain.user.user.QUser
import com.aechak.domain.user.user.QUserProfile
import com.querydsl.core.types.Projections
import com.querydsl.core.types.dsl.BooleanExpression
import com.querydsl.core.types.dsl.NumberExpression
import com.querydsl.jpa.JPAExpressions
import com.querydsl.jpa.impl.JPAQueryFactory
import jakarta.persistence.EntityManager
import jakarta.persistence.Query
import org.springframework.stereotype.Repository
import java.sql.Timestamp
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

/** visibleUser와 같은 조건의 네이티브 조각. 팔로잉 피드의 원천, 원글 작성자, 리포스트한 사람이 함께 쓴다 */
private fun visibleUserSql(userIdColumn: String): String = "EXISTS (SELECT 1 FROM user_profiles vp WHERE vp.user_id = $userIdColumn)"

/** 팔로잉 피드 원천 src. 나와 내가 팔로우하는 사용자 중 볼 수 있고 숨김 밖인 사용자다 */
private fun followingSourcesSql(hasHidden: Boolean): String =
    """
    WITH src AS (
        SELECT s.user_id
        FROM (SELECT :viewerId AS user_id UNION SELECT f.followee_id FROM sns_follows f WHERE f.follower_id = :viewerId) s
        WHERE ${visibleUserSql("s.user_id")}${if (hasHidden) " AND s.user_id NOT IN (:hidden)" else ""}
    )
    """

/** 드라이버가 시각을 Timestamp로 주는 자리도 있고 LocalDateTime으로 주는 자리도 있어 둘을 함께 받는다 */
private fun Array<*>.time(index: Int): LocalDateTime =
    when (val value = this[index]) {
        is Timestamp -> value.toLocalDateTime()
        is LocalDateTime -> value
        else -> error("시각 칼럼의 타입을 모른다: ${value?.let { it::class }}")
    }

/** user BC의 users, user_profiles를 id 동등 조건으로 읽기만 한다. 숨길 작성자를 SQL에서 빼야 keyset 페이지가 크기만큼 찬다 */
@Repository
class SnsFeedQueryAdapter(
    private val queryFactory: JPAQueryFactory,
    private val em: EntityManager,
) : SnsFeedQueryPort {
    // shortcut: 볼 때마다 원천 사용자의 원글과 리포스트를 모두 묶는다. 한 사용자의 원천 이벤트가 5만 건을 넘거나
    // 팔로잉 피드 P95가 300ms를 넘으면 쓸 때 팔로워별로 넣는 피드 표로 바꾼다. 측정은 피드 부하 테스트에서 한다
    override fun findFollowingPage(
        viewerId: Long,
        hiddenUserIds: Set<Long>,
        anchor: LocalDateTime,
        lastTime: LocalDateTime?,
        lastPostId: Long?,
        limit: Int,
    ): List<SnsFeedEntryView> {
        val hasHidden = hiddenUserIds.isNotEmpty()
        // keyset은 원글마다 묶은 뒤에 건다. 묶기 전에 거르면 커서 앞뒤에 이벤트가 있는 원글이 옛 시각으로 다음 페이지에 다시 나온다
        val sql =
            followingSourcesSql(hasHidden) +
                """
                SELECT g.post_id, p.public_id, g.t
                FROM (
                    SELECT ev.post_id, MAX(ev.at) AS t
                    FROM (
                        SELECT sp.id AS post_id, sp.created_at AS at
                        FROM sns_posts sp
                        WHERE sp.author_id IN (SELECT user_id FROM src) AND sp.status = :published AND sp.created_at <= :anchor
                        UNION ALL
                        SELECT r.post_id, r.created_at
                        FROM sns_reposts r
                        WHERE r.reposter_id IN (SELECT user_id FROM src) AND r.created_at <= :anchor
                    ) ev
                    GROUP BY ev.post_id
                ) g
                JOIN sns_posts p ON p.id = g.post_id
                WHERE p.status = :published AND ${visibleUserSql("p.author_id")}
                """ +
                (if (hasHidden) " AND p.author_id NOT IN (:hidden)" else "") +
                (if (lastTime != null) " AND (g.t < :lastTime OR (g.t = :lastTime AND g.post_id < :lastPostId))" else "") +
                " ORDER BY g.t DESC, g.post_id DESC LIMIT :limit"
        val query =
            followingQuery(sql, viewerId, hiddenUserIds, anchor)
                .setParameter("published", PostStatus.PUBLISHED.name)
                .setParameter("limit", limit)
        if (lastTime != null) query.setParameter("lastTime", lastTime).setParameter("lastPostId", lastPostId)
        return query.resultList.map { row ->
            (row as Array<*>).let { SnsFeedEntryView((it[0] as Number).toLong(), it[1] as String, it.time(2)) }
        }
    }

    override fun findLatestReposts(
        viewerId: Long,
        hiddenUserIds: Set<Long>,
        anchor: LocalDateTime,
        postIds: Collection<Long>,
    ): List<SnsFeedRepostView> {
        val sql =
            followingSourcesSql(hiddenUserIds.isNotEmpty()) +
                """
                SELECT x.post_id, u.public_id, up.nickname, x.created_at
                FROM (
                    SELECT r.post_id, r.reposter_id, r.created_at,
                           ROW_NUMBER() OVER (PARTITION BY r.post_id ORDER BY r.created_at DESC, r.id DESC) AS rn
                    FROM sns_reposts r
                    WHERE r.post_id IN (:postIds) AND r.reposter_id IN (SELECT user_id FROM src) AND r.created_at <= :anchor
                ) x
                JOIN users u ON u.id = x.reposter_id
                JOIN user_profiles up ON up.user_id = x.reposter_id
                WHERE x.rn = 1
                """
        return followingQuery(sql, viewerId, hiddenUserIds, anchor)
            .setParameter("postIds", postIds)
            .resultList
            .map { row ->
                (row as Array<*>).let { SnsFeedRepostView((it[0] as Number).toLong(), it[1] as String, it[2] as String, it.time(3)) }
            }
    }

    private fun followingQuery(
        sql: String,
        viewerId: Long,
        hiddenUserIds: Set<Long>,
        anchor: LocalDateTime,
    ): Query {
        val query =
            em
                .createNativeQuery(sql)
                .setParameter("viewerId", viewerId)
                .setParameter("anchor", anchor)
        if (hiddenUserIds.isNotEmpty()) query.setParameter("hidden", hiddenUserIds)
        return query
    }

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

    override fun findUserPostPage(
        authorId: Long,
        tab: SnsPostTab,
        lastCreatedAt: LocalDateTime?,
        lastPostId: Long?,
        limit: Int,
    ): List<SnsFeedEntryView> =
        queryFactory
            .select(Projections.constructor(SnsFeedEntryView::class.java, post.id, post.publicId, post.createdAt))
            .from(post)
            .where(
                post.authorId.eq(authorId),
                post.mediaType.eq(PostMediaType.VIDEO).takeIf { tab == SnsPostTab.VIDEOS },
                post.status.eq(PostStatus.PUBLISHED),
                lastCreatedAt?.let {
                    post.createdAt.lt(it).or(post.createdAt.eq(it).and(post.id.lt(lastPostId)))
                },
            ).orderBy(post.createdAt.desc(), post.id.desc())
            .limit(limit.toLong())
            .fetch()

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
                    post.mediaType,
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
