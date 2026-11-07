package com.aechak.api.schema

import com.aechak.api.support.IntegrationTestBase
import org.assertj.core.api.Assertions.assertThat
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.sql.Connection
import java.sql.DriverManager
import javax.sql.DataSource

/**
 * [통합] V105가 FULLTEXT 인덱스가 있는 products에 적용되는지 본다.
 *
 * 깨지면 배포 환경의 api가 부팅하지 못한다. 빈 DB에서는 이 시점에 products가 없어 ALTER를 건너뛰므로
 * 다른 테스트로는 드러나지 않는다.
 */
class V105ProductAdminSaleBlockMigrationIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var dataSource: DataSource

    @Test
    fun `FULLTEXT 인덱스가 있는 products에 판매중지 컬럼을 더한다`() {
        val url = recreateDatabase("v105_products_fulltext")
        flyway(url, target = "104").migrate()
        execute(
            url,
            "CREATE TABLE products (id BIGINT PRIMARY KEY, name VARCHAR(100), " +
                "FULLTEXT INDEX ft_products_name (name) WITH PARSER ngram)",
        )

        flyway(url, target = "105").migrate()

        assertThat(query(url, "SELECT success FROM flyway_schema_history WHERE version = '105'"))
            .describedAs("V105가 성공으로 기록돼야 api가 부팅한다")
            .containsExactly("1")
        assertThat(
            query(
                url,
                "SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() " +
                    "AND TABLE_NAME = 'products' AND COLUMN_NAME LIKE 'admin_sale_block%' ORDER BY COLUMN_NAME",
            ),
        ).describedAs("판매중지 컬럼 넷이 모두 생겨야 한다")
            .containsExactly(
                "admin_sale_block_previous_status",
                "admin_sale_block_reason",
                "admin_sale_blocked_at",
                "admin_sale_blocked_by_admin_id",
            )
    }

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

    private fun flyway(
        url: String,
        target: String,
    ): Flyway =
        Flyway
            .configure()
            .dataSource(url, ROOT_USER, ROOT_PASSWORD)
            .target(target)
            .load()

    companion object {
        // MySQLContainer의 기본 root 계정. 테스트 전용 계정은 DB를 만들 권한이 없다.
        private const val ROOT_USER = "root"
        private const val ROOT_PASSWORD = "test"
    }
}
