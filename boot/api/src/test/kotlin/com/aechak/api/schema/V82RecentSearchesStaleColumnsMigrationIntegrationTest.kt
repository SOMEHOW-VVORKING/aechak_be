package com.aechak.api.schema

import com.aechak.api.support.IntegrationTestConfig
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.MigrationVersion
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.sql.Connection
import java.sql.ResultSet
import javax.sql.DataSource

/**
 * V82가 recent_searches의 옛 컬럼을 지우는지 보는 회귀 테스트.
 * 깨지면 옛 컬럼이 남은 DB에서 최근 검색어 INSERT가 기본값 없음으로 실패한다.
 */
@SpringBootTest(
    properties = [
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.enabled=false",
    ],
)
@Import(IntegrationTestConfig::class)
class V82RecentSearchesStaleColumnsMigrationIntegrationTest {
    @Autowired
    private lateinit var dataSource: DataSource

    @Test
    fun `옛 컬럼이 남은 DB에서는 두 컬럼을 지워 기존 행을 유지하고 INSERT를 살린다`() {
        val url = recreateDatabase("v82_stale_columns")
        migrateUpTo(url, "81")
        execute(
            url,
            """
            ALTER TABLE recent_searches
                ADD COLUMN display_keyword VARCHAR(255) NOT NULL,
                ADD COLUMN normalized_keyword VARCHAR(255) NOT NULL
            """.trimIndent(),
        )
        execute(
            url,
            """
            INSERT INTO recent_searches
                (user_id, keyword, searched_at, created_at, updated_at, display_keyword, normalized_keyword)
            VALUES (1, 'existing', NOW(6), NOW(6), NOW(6), 'existing', 'existing')
            """.trimIndent(),
        )
        assertThatThrownBy { insertKeyword(url, "new") }.hasMessageContaining("display_keyword")

        flyway(url).migrate()

        assertThat(recentSearchColumns(url)).doesNotContain("display_keyword", "normalized_keyword")
        insertKeyword(url, "new")
        assertThat(keywords(url)).containsExactlyInAnyOrder("existing", "new")
    }

    @Test
    fun `옛 컬럼이 없는 DB에서는 컬럼을 바꾸지 않는다`() {
        val url = recreateDatabase("v82_no_stale_columns")
        migrateUpTo(url, "81")
        val columnsBefore = recentSearchColumns(url)

        flyway(url).migrate()

        assertThat(recentSearchColumns(url)).isEqualTo(columnsBefore)
    }

    private fun insertKeyword(
        url: String,
        keyword: String,
    ) = execute(
        url,
        """
        INSERT INTO recent_searches (user_id, keyword, searched_at, created_at, updated_at)
        VALUES (1, '$keyword', NOW(6), NOW(6), NOW(6))
        """.trimIndent(),
    )

    private fun keywords(url: String): List<String> = query(url, "SELECT keyword FROM recent_searches") { it.getString(1) }

    private fun recentSearchColumns(url: String): List<String> =
        query(
            url,
            """
            SELECT COLUMN_NAME FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'recent_searches'
            ORDER BY ORDINAL_POSITION
            """.trimIndent(),
        ) { it.getString(1) }

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
        sql: String,
    ) = connect(url).use { conn ->
        conn.createStatement().use { statement -> statement.execute(sql) }
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

        @JvmStatic
        @DynamicPropertySource
        fun containers(registry: DynamicPropertyRegistry) = IntegrationTestConfig.registerContainers(registry)
    }
}
