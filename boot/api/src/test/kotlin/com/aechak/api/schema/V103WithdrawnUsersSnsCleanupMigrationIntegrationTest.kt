package com.aechak.api.schema

import com.aechak.api.support.IntegrationTestBase
import org.assertj.core.api.Assertions.assertThat
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.MigrationVersion
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import java.sql.Connection
import java.sql.ResultSet
import javax.sql.DataSource

/**
 * V103(탈퇴 정리 전에 탈퇴한 사용자의 SNS 데이터 소급 정리)이 어떤 행을 지우고 어떤 수를 고치는지 본다.
 * 깨지면 탈퇴한 사용자의 팔로우와 반응이 수에 남거나 탈퇴하지 않은 사용자의 행까지 지워진다.
 * 컨텍스트를 새로 띄우지 않으려고 공용 베이스를 쓴다. V103은 별도 DB에 적용한다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class V103WithdrawnUsersSnsCleanupMigrationIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var dataSource: DataSource

    private lateinit var migratedUrl: String

    @BeforeAll
    fun migrateSeededRows() {
        migratedUrl = recreateDatabase("v103_sns_cleanup")
        // users는 Flyway가 만들지 않는다. V102까지 적용한 뒤 V103이 읽는 컬럼만 가진 users를 만들어 심는다
        migrateUpTo(migratedUrl, "102")
        execute(migratedUrl, listOf("CREATE TABLE users (id BIGINT NOT NULL PRIMARY KEY, status VARCHAR(30) NOT NULL)") + seedRows())
        flyway(migratedUrl).migrate()
    }

    @Test
    fun `탈퇴한 사용자의 게시물과 댓글은 사유 WITHDRAWAL로 지워지고 댓글 내용은 남는다`() {
        assertThat(rows("SELECT id, status, deletion_reason, caption FROM sns_posts ORDER BY id"))
            .containsExactly(listOf(W_POST, "DELETED", "WITHDRAWAL", null), listOf(A_POST, "PUBLISHED", null, "A의 글"))
        assertThat(rows("SELECT id, status, deletion_reason, content FROM sns_comments ORDER BY id"))
            .containsExactly(listOf(W_COMMENT, "DELETED", "WITHDRAWAL", "W의 댓글"), listOf(B_COMMENT, "ACTIVE", null, "B의 댓글"))
        assertThat(rows("SELECT post_id FROM sns_post_hashtags")).containsExactly(listOf(A_POST))
    }

    @Test
    fun `탈퇴한 사용자의 관계와 반응만 지워지고 다른 사용자가 탈퇴한 사용자에게 건 차단과 뮤트는 남는다`() {
        assertThat(rows("SELECT follower_id, followee_id FROM sns_follows")).containsExactly(listOf(B, A))
        assertThat(rows("SELECT blocker_id, blocked_id FROM sns_blocks")).containsExactly(listOf(B, W))
        assertThat(rows("SELECT muter_id, muted_id FROM sns_mutes")).containsExactly(listOf(B, W))
        listOf(
            "SELECT reposter_id FROM sns_reposts",
            "SELECT user_id FROM sns_saves",
            "SELECT user_id FROM sns_post_likes",
            "SELECT user_id FROM sns_comment_likes",
        ).forEach { assertThat(rows(it)).describedAs(it).containsExactly(listOf(B)) }
    }

    @Test
    fun `통계 행과 리포스트 수와 태그 수를 남은 행으로 다시 센다`() {
        assertThat(rows("SELECT user_id, follower_count, following_count, post_count FROM sns_user_stats ORDER BY user_id"))
            .containsExactly(listOf(A, 1, 0, 1), listOf(B, 0, 1, 0))
        assertThat(rows("SELECT post_id, repost_count FROM sns_post_stats")).containsExactly(listOf(A_POST, 1))
        assertThat(rows("SELECT name, post_count FROM sns_hashtags")).containsExactly(listOf("산책", 1))
    }

    /** W는 탈퇴했고 A, B는 아니다. 수는 탈퇴 정리 전처럼 W의 행을 포함한 값으로 심는다 */
    private fun seedRows(): List<String> =
        listOf(
            "INSERT INTO users (id, status) VALUES ($W, 'WITHDRAWN'), ($A, 'ACTIVE'), ($B, 'ACTIVE')",
            """
            INSERT INTO sns_posts (id, public_id, author_id, caption, media_type, status, idempotency_key, created_at, updated_at)
            VALUES ($W_POST, 'P1', $W, 'W의 글', 'TEXT', 'PUBLISHED', 'k1', $AT, $AT),
                   ($A_POST, 'P2', $A, 'A의 글', 'TEXT', 'PUBLISHED', 'k2', $AT, $AT)
            """.trimIndent(),
            "INSERT INTO sns_hashtags (id, name, post_count, created_at, updated_at) VALUES (1, '산책', 2, $AT, $AT)",
            "INSERT INTO sns_post_hashtags (post_id, hashtag_id, sort_order, created_at, updated_at) VALUES ($W_POST, 1, 0, $AT, $AT), ($A_POST, 1, 0, $AT, $AT)",
            """
            INSERT INTO sns_comments (id, public_id, post_id, author_id, content, status, idempotency_key, created_at, updated_at)
            VALUES ($W_COMMENT, 'C1', $A_POST, $W, 'W의 댓글', 'ACTIVE', 'k1', $AT, $AT),
                   ($B_COMMENT, 'C2', $A_POST, $B, 'B의 댓글', 'ACTIVE', 'k2', $AT, $AT)
            """.trimIndent(),
            "INSERT INTO sns_follows (follower_id, followee_id, created_at, updated_at) VALUES ($W, $A, $AT, $AT), ($B, $W, $AT, $AT), ($B, $A, $AT, $AT)",
            "INSERT INTO sns_blocks (blocker_id, blocked_id, created_at, updated_at) VALUES ($W, $A, $AT, $AT), ($B, $W, $AT, $AT)",
            "INSERT INTO sns_mutes (muter_id, muted_id, created_at, updated_at) VALUES ($W, $A, $AT, $AT), ($B, $W, $AT, $AT)",
            "INSERT INTO sns_reposts (post_id, reposter_id, created_at, updated_at) VALUES ($A_POST, $W, $AT, $AT), ($A_POST, $B, $AT, $AT)",
            "INSERT INTO sns_saves (user_id, post_id, created_at, updated_at) VALUES ($W, $A_POST, $AT, $AT), ($B, $A_POST, $AT, $AT)",
            "INSERT INTO sns_post_likes (post_id, user_id, created_at, updated_at) VALUES ($A_POST, $W, $AT, $AT), ($A_POST, $B, $AT, $AT)",
            "INSERT INTO sns_comment_likes (comment_id, user_id, created_at, updated_at) VALUES ($B_COMMENT, $W, $AT, $AT), ($W_COMMENT, $B, $AT, $AT)",
            "INSERT INTO sns_post_stats (post_id, repost_count, created_at, updated_at) VALUES ($A_POST, 2, $AT, $AT)",
            """
            INSERT INTO sns_user_stats (user_id, follower_count, following_count, post_count, created_at, updated_at)
            VALUES ($W, 1, 1, 1, $AT, $AT), ($A, 2, 0, 1, $AT, $AT), ($B, 0, 2, 0, $AT, $AT)
            """.trimIndent(),
        )

    /** 행마다 열 값 목록. 숫자는 Long이 아니라 Int로 맞춰 비교한다 */
    private fun rows(sql: String): List<List<Any?>> =
        query(migratedUrl, sql) { rs ->
            (1..rs.metaData.columnCount).map { i -> rs.getObject(i).let { if (it is Number) it.toInt() else it } }
        }

    private fun <T> query(
        url: String,
        sql: String,
        row: (ResultSet) -> T,
    ): List<T> =
        connect(url).use { conn ->
            conn.createStatement().use { statement ->
                val rs = statement.executeQuery(sql)
                val result = mutableListOf<T>()
                while (rs.next()) result += row(rs)
                result
            }
        }

    private fun execute(
        url: String,
        statements: List<String>,
    ) = connect(url).use { conn ->
        conn.createStatement().use { statement ->
            statements.forEach { statement.execute(it) }
        }
    }

    private fun recreateDatabase(name: String): String {
        connect(adminUrl(null)).use { conn ->
            conn.createStatement().use {
                it.execute("DROP DATABASE IF EXISTS $name")
                it.execute("CREATE DATABASE $name DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_0900_ai_ci")
            }
        }
        return adminUrl(name)
    }

    private fun adminUrl(database: String?): String {
        val base = dataSource.connection.use { it.metaData.url }.substringBeforeLast('/')
        return "$base/${database ?: ""}"
    }

    private fun connect(url: String): Connection = java.sql.DriverManager.getConnection(url, ROOT_USER, ROOT_PASSWORD)

    private fun flyway(url: String): Flyway = Flyway.configure().dataSource(url, ROOT_USER, ROOT_PASSWORD).load()

    private fun migrateUpTo(
        url: String,
        version: String,
    ) = Flyway
        .configure()
        .dataSource(url, ROOT_USER, ROOT_PASSWORD)
        .target(MigrationVersion.fromVersion(version))
        .load()
        .migrate()

    companion object {
        // MySQLContainer의 기본 root 계정. 테스트 전용 계정은 DB를 만들 권한이 없다.
        private const val ROOT_USER = "root"
        private const val ROOT_PASSWORD = "test"

        private const val W = 1
        private const val A = 2
        private const val B = 3
        private const val W_POST = 10
        private const val A_POST = 11
        private const val W_COMMENT = 20
        private const val B_COMMENT = 21
        private const val AT = "'2026-01-01 00:00:00'"
    }
}
