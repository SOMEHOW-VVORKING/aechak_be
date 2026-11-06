package com.aechak.infra.persistence.sns.search

import com.aechak.application.sns.port.SnsPostMatch
import com.aechak.application.sns.port.SnsSearchQueryPort
import com.aechak.application.sns.port.view.SnsFeedEntryView
import com.aechak.domain.sns.post.enums.PostStatus
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
        repostWeight: Int,
        limit: Int,
    ): List<Long> {
        val sql = match.toSql()
        return nativeQuery(
            "SELECT p.id FROM sns_posts p LEFT JOIN sns_post_stats s ON s.post_id = p.id WHERE ${baseWhere(sql)}" +
                " ORDER BY COALESCE(s.repost_count, 0) * :repostWeight DESC, p.created_at DESC, p.id DESC LIMIT :limit",
            sql,
        ).setParameter("repostWeight", repostWeight)
            .setParameter("limit", limit)
            .resultList
            .map { (it as Number).toLong() }
    }

    override fun count(match: SnsPostMatch): Long {
        val sql = match.toSql()
        return (nativeQuery("SELECT COUNT(*) FROM sns_posts p WHERE ${baseWhere(sql)}", sql).singleResult as Number).toLong()
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
