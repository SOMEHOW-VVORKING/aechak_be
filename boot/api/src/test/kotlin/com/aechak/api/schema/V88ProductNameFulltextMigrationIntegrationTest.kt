package com.aechak.api.schema

import com.aechak.api.support.IntegrationTestBase
import org.assertj.core.api.Assertions.assertThat
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.MigrationVersion
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.sql.Connection
import java.sql.DriverManager
import javax.sql.DataSource

/**
 * V88(상품명 ngram FULLTEXT 인덱스)의 가드 분기를 본다.
 * 깨지면 products가 없는 새 DB에서 마이그레이션이 실패하거나, 기존 DB에 인덱스가 생기지 않아 MATCH가 오류 1191을 낸다.
 * 컨텍스트를 새로 띄우지 않으려고 공용 베이스를 쓴다. V88는 별도 DB에 적용한다.
 */
class V88ProductNameFulltextMigrationIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var dataSource: DataSource

    @Test
    fun `products가 없는 DB에서는 아무것도 만들지 않고 성공으로 기록된다`() {
        val url = recreateDatabase("v88_no_products")

        flyway(url).migrate()

        assertThat(scalar(url, "SELECT COUNT(*) FROM information_schema.TABLES $IN_SCHEMA AND TABLE_NAME = 'products'")).isEqualTo("0")
        assertThat(scalar(url, "SELECT success FROM flyway_schema_history WHERE version = '88'")).isEqualTo("1")
    }

    @Test
    fun `변경 직전 DDL의 products에 ngram FULLTEXT 인덱스를 만든다`() {
        val url = recreateDatabase("v88_products")
        migrateBeforeV88(url)
        execute(url, preChangeProductsDdl())
        assertThat(fulltextIndexes(url)).describedAs("적용 전에는 인덱스가 없어야 ALTER 분기가 실제로 돈다").isEmpty()

        flyway(url).migrate()

        assertThat(fulltextIndexes(url)).containsExactly("ft_products_name:FULLTEXT:1:name")
        assertThat(showCreateProducts(url)).contains("WITH PARSER `ngram`")
    }

    @Test
    fun `배포 전에 인덱스를 미리 만든 DB에서는 건너뛴다`() {
        val url = recreateDatabase("v88_prebuilt")
        migrateBeforeV88(url)
        execute(url, preChangeProductsDdl())
        execute(url, "ALTER TABLE products ADD FULLTEXT INDEX ft_products_name (name) WITH PARSER ngram")

        flyway(url).migrate()

        assertThat(fulltextIndexes(url)).containsExactly("ft_products_name:FULLTEXT:1:name")
        assertThat(scalar(url, "SELECT success FROM flyway_schema_history WHERE version = '88'")).isEqualTo("1")
    }

    /** 공용 테스트 스키마의 products DDL에서 공용 테스트 설정이 더한 FULLTEXT 인덱스를 뺀 것 */
    private fun preChangeProductsDdl(): String {
        val current = showCreateProducts(adminUrl(dataSource.connection.use { it.catalog }))
        val ddl = current.replace(Regex(",\\n\\s*FULLTEXT KEY[^,\\n]*"), "")
        assertThat(current).describedAs("공용 테스트 스키마에는 인덱스가 있어야 한다").contains("FULLTEXT KEY")
        assertThat(ddl).doesNotContain("FULLTEXT")
        return ddl
    }

    private fun fulltextIndexes(url: String): List<String> =
        query(
            url,
            "SELECT CONCAT(INDEX_NAME, ':', INDEX_TYPE, ':', SEQ_IN_INDEX, ':', COLUMN_NAME) FROM information_schema.STATISTICS " +
                "$IN_SCHEMA AND TABLE_NAME = 'products' AND INDEX_TYPE = 'FULLTEXT' ORDER BY SEQ_IN_INDEX",
        )

    private fun showCreateProducts(url: String): String =
        connect(url).use { conn ->
            conn.createStatement().use { statement ->
                statement.executeQuery("SHOW CREATE TABLE products").use { rs ->
                    rs.next()
                    rs.getString(2)
                }
            }
        }

    private fun scalar(
        url: String,
        sql: String,
    ): String = query(url, sql).single()

    private fun query(
        url: String,
        sql: String,
    ): List<String> =
        connect(url).use { conn ->
            conn.createStatement().use { statement ->
                val rs = statement.executeQuery(sql)
                val result = mutableListOf<String>()
                while (rs.next()) result += rs.getString(1)
                result
            }
        }

    private fun execute(
        url: String,
        sql: String,
    ) = connect(url).use { conn -> conn.createStatement().use { it.execute(sql) } }

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

    private fun connect(url: String): Connection = DriverManager.getConnection(url, ROOT_USER, ROOT_PASSWORD)

    private fun flyway(url: String): Flyway = Flyway.configure().dataSource(url, ROOT_USER, ROOT_PASSWORD).load()

    /** V88 바로 앞 버전까지 적용한다 */
    private fun migrateBeforeV88(url: String) {
        val previous =
            flyway(url)
                .info()
                .all()
                .map { it.version }
                .filter { it < V88 }
                .max()
        Flyway
            .configure()
            .dataSource(url, ROOT_USER, ROOT_PASSWORD)
            .target(previous)
            .load()
            .migrate()
    }

    companion object {
        // MySQLContainer의 기본 root 계정. 테스트 전용 계정은 DB를 만들 권한이 없다.
        private const val ROOT_USER = "root"
        private const val ROOT_PASSWORD = "test"

        private const val IN_SCHEMA = "WHERE TABLE_SCHEMA = DATABASE()"
        private val V88: MigrationVersion = MigrationVersion.fromVersion("88")
    }
}
