package com.aechak.infra.persistence.sns.search

import com.aechak.application.sns.port.SnsPostMatch
import com.aechak.application.sns.port.SnsSearchQueryPort
import com.aechak.application.sns.port.view.SnsFeedEntryView
import com.aechak.application.sns.port.view.SnsSearchHashtagView
import com.aechak.application.sns.port.view.SnsSearchUserView
import com.aechak.domain.sns.post.enums.PostStatus
import com.aechak.domain.user.user.enums.UserStatus
import com.aechak.infra.persistence.sns.feed.time
import com.aechak.infra.persistence.sns.feed.visibleUserSql
import com.aechak.infra.persistence.sns.list.nicknameContainsPattern
import jakarta.persistence.EntityManager
import jakarta.persistence.Query
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

/** 검색 조건의 SQL 조각과 그 파라미터 */
private class MatchSql(
    val where: String,
    val params: Map<String, Any>,
)

/** 단어마다 캡션이나 그 글의 해시태그 이름에 부분 일치. 캡션은 utf8mb4_0900_ai_ci라 대소문자를 가리지 않고 태그 이름은 소문자로 정규화돼 있다 */
private fun SnsPostMatch.toSql(): MatchSql =
    when (this) {
        is SnsPostMatch.Words -> {
            val params = mutableMapOf<String, Any>()
            val where =
                words.indices.joinToString(" AND ") { i ->
                    params["w$i"] = nicknameContainsPattern(words[i])
                    // #만 있는 단어는 태그 표기가 비어 모든 태그와 맞으므로 캡션으로만 찾는다
                    if (tagWords[i].isEmpty()) return@joinToString "p.caption LIKE :w$i ESCAPE '\\\\'"
                    params["t$i"] = nicknameContainsPattern(tagWords[i])
                    """
                    (p.caption LIKE :w$i ESCAPE '\\' OR EXISTS (
                        SELECT 1 FROM sns_post_hashtags ph JOIN sns_hashtags h ON h.id = ph.hashtag_id
                        WHERE ph.post_id = p.id AND h.name LIKE :t$i ESCAPE '\\'))
                    """
                }
            MatchSql(where, params)
        }

        is SnsPostMatch.Tag -> {
            MatchSql(
                "EXISTS (SELECT 1 FROM sns_post_hashtags ph WHERE ph.post_id = p.id AND ph.hashtag_id = :tagId)",
                mapOf("tagId" to hashtagId),
            )
        }
    }

/** PUBLISHED 원글 중 작성자가 볼 수 있고 검색 조건에 맞는 글 */
private fun baseWhere(match: MatchSql): String = "p.status = :published AND ${visibleUserSql("p.author_id")} AND ${match.where}"

/**
 * 게시물 p의 공개 댓글 수. 인기 피드 점수의 publicCommentCount와 같은 규칙으로 ACTIVE 댓글과 대댓글을 세고
 * 게시물 작성자가 뮤트한 사용자의 댓글과 그 사용자의 최상위 댓글 아래 대댓글(섀도우)만 뺀다. 작성자 본인 댓글도 센다.
 */
private const val PUBLIC_COMMENT_COUNT =
    "SELECT COUNT(*) FROM sns_comments c LEFT JOIN sns_comments r ON r.id = c.parent_id" +
        " WHERE c.post_id = p.id AND c.status = 'ACTIVE'" +
        " AND NOT EXISTS (SELECT 1 FROM sns_mutes cm WHERE cm.muter_id = p.author_id AND cm.muted_id = c.author_id)" +
        " AND (c.parent_id IS NULL OR NOT EXISTS (SELECT 1 FROM sns_mutes rm WHERE rm.muter_id = p.author_id AND rm.muted_id = r.author_id))"

// shortcut: 캡션과 태그 이름을 앞에 와일드카드가 붙은 LIKE로 찾아 맞는 글을 고를 때 sns_posts를 끝까지 읽는다.
// PUBLISHED 원글이 10만 건을 넘거나 검색 P95가 500ms를 넘으면 ngram FULLTEXT 색인이나 검색 엔진으로 옮긴다
@Repository
class SnsSearchQueryAdapter(
    private val em: EntityManager,
) : SnsSearchQueryPort {
    override fun findLatestPage(
        match: SnsPostMatch,
        hiddenUserIds: Set<Long>,
        lastCreatedAt: LocalDateTime?,
        lastPostId: Long?,
        limit: Int,
    ): List<SnsFeedEntryView> {
        val sql = match.toSql()
        val query =
            nativeQuery(
                "SELECT p.id, p.public_id, p.created_at FROM sns_posts p WHERE ${baseWhere(sql)}" +
                    (if (hiddenUserIds.isNotEmpty()) " AND p.author_id NOT IN (:hidden)" else "") +
                    (
                        if (lastCreatedAt !=
                            null
                        ) {
                            " AND (p.created_at < :lastCreatedAt OR (p.created_at = :lastCreatedAt AND p.id < :lastPostId))"
                        } else {
                            ""
                        }
                    ) +
                    " ORDER BY p.created_at DESC, p.id DESC LIMIT :limit",
                sql,
            ).setParameter("limit", limit)
        if (hiddenUserIds.isNotEmpty()) query.setParameter("hidden", hiddenUserIds)
        if (lastCreatedAt != null) query.setParameter("lastCreatedAt", lastCreatedAt).setParameter("lastPostId", lastPostId)
        return query.resultList.map { row ->
            (row as Array<*>).let { SnsFeedEntryView((it[0] as Number).toLong(), it[1] as String, it.time(2)) }
        }
    }

    override fun findPopularRanking(
        match: SnsPostMatch,
        likeWeight: Int,
        commentWeight: Int,
        repostWeight: Int,
        limit: Int,
    ): List<Long> {
        val sql = match.toSql()
        return nativeQuery(
            "SELECT p.id FROM sns_posts p LEFT JOIN sns_post_stats s ON s.post_id = p.id WHERE ${baseWhere(sql)}" +
                " ORDER BY (SELECT COUNT(*) FROM sns_post_likes pl WHERE pl.post_id = p.id) * :likeWeight" +
                " + ($PUBLIC_COMMENT_COUNT) * :commentWeight" +
                " + COALESCE(s.repost_count, 0) * :repostWeight DESC, p.created_at DESC, p.id DESC LIMIT :limit",
            sql,
        ).setParameter("likeWeight", likeWeight)
            .setParameter("commentWeight", commentWeight)
            .setParameter("repostWeight", repostWeight)
            .setParameter("limit", limit)
            .resultList
            .map { (it as Number).toLong() }
    }

    override fun count(match: SnsPostMatch): Long {
        val sql = match.toSql()
        return (nativeQuery("SELECT COUNT(*) FROM sns_posts p WHERE ${baseWhere(sql)}", sql).singleResult as Number).toLong()
    }

    // shortcut: 닉네임을 앞에 와일드카드가 붙은 LIKE로 찾아 user_profiles를 끝까지 읽고 맞는 행 전체를 정렬한다.
    // 프로필이 10만 명을 넘거나 계정 검색 P95가 300ms를 넘으면 ngram FULLTEXT 색인으로 바꾼다
    override fun findUserPage(
        nickname: String,
        viewerId: Long?,
        lastBucket: Int?,
        lastFollowerCount: Long?,
        lastPublicId: String?,
        limit: Int,
    ): List<SnsSearchUserView> {
        val blockingMe =
            if (viewerId !=
                null
            ) {
                "EXISTS (SELECT 1 FROM sns_blocks b WHERE b.blocker_id = u.id AND b.blocked_id = :viewerId)"
            } else {
                "0"
            }
        val sql =
            """
            SELECT x.id, x.public_id, x.nickname, x.profile_image_key, x.fc, x.bucket, x.blocking_me
            FROM (
                SELECT u.id, u.public_id, up.nickname, up.profile_image_key, COALESCE(st.follower_count, 0) AS fc,
                       CASE WHEN up.nickname = :exact THEN 0 WHEN up.nickname LIKE :prefix ESCAPE '\\' THEN 1 ELSE 2 END AS bucket,
                       $blockingMe AS blocking_me
                FROM user_profiles up
                JOIN users u ON u.id = up.user_id
                LEFT JOIN sns_user_stats st ON st.user_id = u.id
                WHERE up.nickname LIKE :contains ESCAPE '\\' AND u.status <> '${UserStatus.BANNED.name}'
            ) x
            WHERE 1 = 1
            """ +
                // 내가 차단만 한 사용자는 빼고 나를 차단한 사용자는 내가 차단했어도 기본 프로필로 남긴다
                (
                    if (viewerId != null) {
                        " AND (x.blocking_me = 1 OR NOT EXISTS (SELECT 1 FROM sns_blocks b2 WHERE b2.blocker_id = :viewerId AND b2.blocked_id = x.id))"
                    } else {
                        ""
                    }
                ) +
                (
                    if (lastBucket != null) {
                        " AND (x.bucket > :lastBucket OR (x.bucket = :lastBucket AND (x.fc < :lastFc OR (x.fc = :lastFc AND x.public_id > :lastPublicId))))"
                    } else {
                        ""
                    }
                ) +
                " ORDER BY x.bucket, x.fc DESC, x.public_id LIMIT :limit"
        val contains = nicknameContainsPattern(nickname)
        val query =
            em
                .createNativeQuery(sql)
                .setParameter("exact", nickname)
                // 앞의 %만 떼면 시작 일치 패턴이다
                .setParameter("prefix", contains.removePrefix("%"))
                .setParameter("contains", contains)
                .setParameter("limit", limit)
        if (viewerId != null) query.setParameter("viewerId", viewerId)
        if (lastBucket != null) {
            query
                .setParameter(
                    "lastBucket",
                    lastBucket,
                ).setParameter("lastFc", lastFollowerCount)
                .setParameter("lastPublicId", lastPublicId)
        }
        return query.resultList.map { row ->
            (row as Array<*>).let {
                SnsSearchUserView(
                    userId = (it[0] as Number).toLong(),
                    publicId = it[1] as String,
                    nickname = it[2] as String,
                    profileImageKey = it[3] as String?,
                    followerCount = (it[4] as Number).toLong(),
                    bucket = (it[5] as Number).toInt(),
                    blockingMe = (it[6] as Number).toInt() == 1,
                )
            }
        }
    }

    override fun findHashtagPage(
        name: String,
        lastPostCount: Int?,
        lastName: String?,
        limit: Int,
    ): List<SnsSearchHashtagView> {
        val query =
            em
                .createNativeQuery(
                    "SELECT h.name, h.post_count FROM sns_hashtags h WHERE h.post_count > 0 AND h.name LIKE :contains ESCAPE '\\\\'" +
                        (
                            if (lastPostCount !=
                                null
                            ) {
                                " AND (h.post_count < :lastCount OR (h.post_count = :lastCount AND h.name > :lastName))"
                            } else {
                                ""
                            }
                        ) +
                        " ORDER BY h.post_count DESC, h.name LIMIT :limit",
                ).setParameter("contains", nicknameContainsPattern(name))
                .setParameter("limit", limit)
        if (lastPostCount != null) query.setParameter("lastCount", lastPostCount).setParameter("lastName", lastName)
        return query.resultList.map { row -> (row as Array<*>).let { SnsSearchHashtagView(it[0] as String, (it[1] as Number).toInt()) } }
    }

    private fun nativeQuery(
        statement: String,
        match: MatchSql,
    ): Query {
        val query = em.createNativeQuery(statement).setParameter("published", PostStatus.PUBLISHED.name)
        match.params.forEach { (name, value) -> query.setParameter(name, value) }
        return query
    }
}
