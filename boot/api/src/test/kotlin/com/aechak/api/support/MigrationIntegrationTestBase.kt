package com.aechak.api.support

import org.flywaydb.core.Flyway
import org.flywaydb.core.api.MigrationVersion
import org.springframework.beans.factory.annotation.Autowired
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import javax.sql.DataSource

/**
 * 마이그레이션 하나를 격리된 DB에 올려 보는 통합 테스트의 공용 베이스
 *
 * ## 왜 격리된 DB가 필요한가
 *
 * `products`·`users` 같은 엔티티 테이블은 Flyway가 아니라 ddl-auto가 만든다. 그런데 Spring Boot는
 * Flyway를 Hibernate보다 먼저 돌린다. 그래서 테스트 컨텍스트의 DB에서는 Flyway가 돌 때 그
 * 테이블들이 없고, 그것을 조건으로 다는 마이그레이션은 조건이 거짓이 되어 `SELECT 1`로 떨어진다.
 * **통과하지만 실행되지는 않는다.**
 *
 * 그 SQL을 실제로 돌려 보려면 앞 번호까지 올린 DB를 따로 만들고, 거기에 dev와 같은 모양의
 * ddl-auto 테이블을 손으로 심은 뒤, 대상 마이그레이션만 적용해야 한다. 이 베이스가 그 절차를 준다.
 *
 * ## 쓰는 법
 *
 * ```
 * val url = recreateDatabase("v20_snapshot")
 * migrateBefore(url, "20")
 * execute(url, "CREATE TABLE order_items (...)")   // ddl-auto 몫을 dev 모양으로
 * migrateTo(url, "20")
 * assertThat(scalar(url, "SELECT ...")).isEqualTo(...)
 * ```
 *
 * 컨텍스트를 새로 띄우지 않으려고 공용 통합 베이스를 상속한다. 컨테이너는 같은 것을 쓰고
 * DB만 따로 만든다.
 */
abstract class MigrationIntegrationTestBase : IntegrationTestBase() {
    @Autowired
    private lateinit var dataSource: DataSource

    /** 같은 이름의 DB를 지우고 다시 만들어 그 JDBC URL을 준다. 테스트마다 다른 이름을 쓴다. */
    protected fun recreateDatabase(name: String): String {
        connect(adminUrl(null)).use { conn ->
            conn.createStatement().use { statement ->
                statement.execute("DROP DATABASE IF EXISTS $name")
                statement.execute("CREATE DATABASE $name DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_0900_ai_ci")
            }
        }
        return adminUrl(name)
    }

    /** 전체를 적용한다. */
    protected fun migrateAll(url: String) {
        flyway(url).migrate()
    }

    /** 이 버전까지 적용한다(이 버전을 포함한다). */
    protected fun migrateTo(
        url: String,
        version: String,
    ) {
        flyway(url, version).migrate()
    }

    /**
     * 이 버전 바로 앞까지 적용한다.
     *
     * 직전 번호를 손으로 적지 않고 Flyway가 아는 목록에서 고른다. 번호 사이가 비어 있거나(V104가
     * 없는데 V105를 보려는 경우) 중간 번호가 새로 끼어들어도 그대로 동작한다.
     */
    protected fun migrateBefore(
        url: String,
        version: String,
    ) {
        val target = MigrationVersion.fromVersion(version)
        val previous =
            flyway(url)
                .info()
                .all()
                .mapNotNull { it.version }
                .filter { it < target }
                .maxOrNull()
        requireNotNull(previous) { "V$version 앞에 적용할 마이그레이션이 없다" }
        flyway(url, previous.version).migrate()
    }

    protected fun flyway(
        url: String,
        target: String? = null,
    ): Flyway =
        Flyway
            .configure()
            .dataSource(url, ROOT_USER, ROOT_PASSWORD)
            .let { if (target == null) it else it.target(target) }
            .load()

    protected fun execute(
        url: String,
        vararg statements: String,
    ) = execute(url, statements.toList())

    protected fun execute(
        url: String,
        statements: List<String>,
    ) = connect(url).use { conn ->
        conn.createStatement().use { statement -> statements.forEach { statement.execute(it) } }
    }

    /** 첫 열만 문자열로 모은다. 스키마를 information_schema로 확인할 때 쓴다. */
    protected fun query(
        url: String,
        sql: String,
    ): List<String> = query(url, sql) { it.getString(1) }

    protected fun <T> query(
        url: String,
        sql: String,
        map: (ResultSet) -> T,
    ): List<T> =
        connect(url).use { conn ->
            conn.createStatement().use { statement ->
                statement.executeQuery(sql).use { rs ->
                    val rows = mutableListOf<T>()
                    while (rs.next()) rows += map(rs)
                    rows
                }
            }
        }

    /** 한 행 한 열을 기대한다. 두 행이 오면 그 자리에서 깨져야 하므로 single을 쓴다. */
    protected fun scalar(
        url: String,
        sql: String,
    ): String? = query(url, sql).single()

    protected fun showCreate(
        url: String,
        table: String,
    ): String = query(url, "SHOW CREATE TABLE $table") { it.getString(2) }.single()

    /** 그 버전이 성공으로 기록됐는지. 1이 아니면 부팅이 그 자리에서 멈춘다. */
    protected fun appliedSuccessfully(
        url: String,
        version: String,
    ): List<String> = query(url, "SELECT success FROM flyway_schema_history WHERE version = '$version'")

    protected fun connect(url: String): Connection = DriverManager.getConnection(url, ROOT_USER, ROOT_PASSWORD)

    private fun adminUrl(database: String?): String {
        val base = dataSource.connection.use { it.metaData.url }.substringBeforeLast('/')
        return "$base/${database ?: ""}"
    }

    companion object {
        // MySQLContainer의 기본 root 계정. 테스트 전용 계정은 DB를 만들 권한이 없다.
        private const val ROOT_USER = "root"
        private const val ROOT_PASSWORD = "test"

        /** information_schema 조회에서 반복되는 조건. */
        protected const val IN_SCHEMA = "WHERE TABLE_SCHEMA = DATABASE()"
    }
}
