package com.aechak.api.schema

import com.aechak.api.support.IntegrationTestConfig
import org.assertj.core.api.Assertions.assertThat
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.MigrationVersion
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.io.File
import java.sql.Connection
import java.sql.ResultSet
import javax.sql.DataSource

/**
 * V45(제재 이력 테이블 + users.sanction_until)의 안전성 검증.
 * 깨지면 "dev에 적용하면 제재 스키마가 엔티티와 갈린다" 또는 "재실행이 스키마를 바꾼다"는 뜻이다.
 *
 * 세 스키마를 대조한다.
 *  - fresh  : 빈 DB에 Flyway 전체 적용 — 기준
 *  - entity : Hibernate가 엔티티 선언대로 만들었을 DDL
 *  - legacy : dev 재현 — ddl-auto가 users를 만들어 둔 DB에 V45를 적용
 */
@SpringBootTest(
    properties = [
        // DB를 건드리지 않고 Hibernate가 만들 DDL만 파일로 받는다.
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.enabled=false",
        "spring.jpa.properties.jakarta.persistence.schema-generation.scripts.action=create",
        "spring.jpa.properties.jakarta.persistence.schema-generation.scripts.create-target=$V45_ENTITY_SCHEMA_SCRIPT",
        "spring.jpa.properties.hibernate.hbm2ddl.delimiter=;",
    ],
)
@Import(IntegrationTestConfig::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class V45UserSanctionsMigrationIntegrationTest {
    @Autowired
    private lateinit var dataSource: DataSource

    private lateinit var freshUrl: String
    private lateinit var entityUrl: String
    private lateinit var legacyUrl: String

    @BeforeAll
    fun prepareSchemas() {
        freshUrl = recreateDatabase("v45_fresh")
        flyway(freshUrl).migrate()

        entityUrl = recreateDatabase("v45_entity")
        execute(entityUrl, entityStatementsOf(SANCTIONS_TABLE))

        legacyUrl = recreateDatabase("v45_legacy")
        migrateUpTo(legacyUrl, "35")
        execute(legacyUrl, entityStatementsOf("users") + seedUser())
        flyway(legacyUrl).migrate()
    }

    @Test
    fun `빈 DB에 Flyway 전체 적용이 성공하고 제재 이력 테이블이 생긴다`() {
        val applied = query(freshUrl, "SELECT version FROM flyway_schema_history WHERE success = 1") { it.getString(1) }
        assertThat(applied).describedAs("성공으로 기록된 버전").contains("45")
        assertThat(tableExists(freshUrl, SANCTIONS_TABLE)).describedAs("$SANCTIONS_TABLE 존재").isTrue()
    }

    @Test
    fun `users가 없는 빈 DB에서는 컬럼 추가를 건너뛰고 성공한다`() {
        assertThat(tableExists(freshUrl, "users")).describedAs("빈 DB의 users 테이블").isFalse()
    }

    @Test
    fun `ddl-auto가 만든 users에 sanction_until이 NULL 허용으로 추가된다`() {
        val column =
            query(
                legacyUrl,
                "SELECT COLUMN_TYPE, IS_NULLABLE FROM information_schema.COLUMNS " +
                    "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND COLUMN_NAME = 'sanction_until'",
            ) { "${it.getString(1).lowercase()}/${it.getString(2)}" }

        assertThat(column).describedAs("users.sanction_until 타입과 NULL 허용").containsExactly("datetime(6)/YES")
        assertThat(query(legacyUrl, "SELECT COUNT(*) FROM users WHERE sanction_until IS NULL") { it.getInt(1) })
            .describedAs("기존 행의 제재 만료 시각은 비어 있어야 한다")
            .containsExactly(1)
    }

    @Test
    fun `Flyway가 만든 제재 이력 테이블이 엔티티 선언과 일치한다`() {
        val actual = columnsOf(freshUrl, SANCTIONS_TABLE)
        val expected = columnsOf(entityUrl, SANCTIONS_TABLE)

        assertThat(actual.keys).describedAs("컬럼 집합").isEqualTo(expected.keys)
        expected.forEach { (column, expectedShape) ->
            val actualShape = actual.getValue(column)
            assertThat(actualShape.nullable).describedAs("%s NULL 허용", column).isEqualTo(expectedShape.nullable)
            assertThat(actualShape.extra).describedAs("%s EXTRA", column).isEqualTo(expectedShape.extra)
            assertTypeCompatible(column, actualShape.type, expectedShape.type)
        }
        assertThat(indexesOf(freshUrl, SANCTIONS_TABLE))
            .describedAs("인덱스")
            .isEqualTo(indexesOf(entityUrl, SANCTIONS_TABLE))
    }

    @Test
    fun `V45를 다시 실행해도 스키마가 그대로고 세션 설정이 남지 않는다`() {
        val before = columnsOf(freshUrl, SANCTIONS_TABLE) to indexesOf(freshUrl, SANCTIONS_TABLE)

        val timeouts =
            connect(freshUrl).use { conn ->
                conn.createStatement().use { statement ->
                    val original = scalar(statement.executeQuery("SELECT @@SESSION.lock_wait_timeout"))
                    statementsOf(migrationSql()).forEach { statement.execute(it) }
                    original to scalar(statement.executeQuery("SELECT @@SESSION.lock_wait_timeout"))
                }
            }

        assertThat(columnsOf(freshUrl, SANCTIONS_TABLE) to indexesOf(freshUrl, SANCTIONS_TABLE))
            .describedAs("재실행 뒤 스키마")
            .isEqualTo(before)
        assertThat(timeouts.second).describedAs("재실행 뒤 lock_wait_timeout").isEqualTo(timeouts.first)
    }

    /** Hibernate는 enum을 네이티브 enum으로, 마이그레이션은 VARCHAR로 만든다 — 길이가 모든 값을 담는지까지 본다. */
    private fun assertTypeCompatible(
        column: String,
        actual: String,
        expected: String,
    ) {
        if (actual == expected) return
        val kinds = setOf(actual.substringBefore("("), expected.substringBefore("("))
        assertThat(kinds).describedAs("%s 타입 (%s vs %s)", column, actual, expected).isEqualTo(setOf("enum", "varchar"))
        val (enumType, varcharType) = if (actual.startsWith("enum")) actual to expected else expected to actual
        val longestEnumValue =
            enumType
                .substringAfter("(")
                .substringBeforeLast(")")
                .split(",")
                .maxOf { it.trim().removeSurrounding("'").length }
        assertThat(varcharType.substringAfter("(").substringBefore(")").toInt())
            .describedAs("%s VARCHAR 길이가 enum 값(%s)을 담아야 한다", column, enumType)
            .isGreaterThanOrEqualTo(longestEnumValue)
    }

    /**
     * 엔티티 DDL 스크립트에서 대상 테이블 문장(생성 + 인덱스)만 고른다.
     * FK는 뺀다 — 팀 규약상 마이그레이션은 FK를 만들지 않는다. 테이블 생성을 인덱스 생성보다 먼저 둔다.
     */
    private fun entityStatementsOf(table: String): List<String> {
        val target = Regex("\\b`?$table`?\\b", RegexOption.IGNORE_CASE)
        val statements =
            statementsOf(File(V45_ENTITY_SCHEMA_SCRIPT).readText())
                .filter { target.containsMatchIn(it) && !FOREIGN_KEY.containsMatchIn(it) }
                .distinct() // Hibernate가 실행마다 스크립트 파일에 이어 쓴다
                .sortedBy { if (it.startsWith("create table", ignoreCase = true)) 0 else 1 }
        assertThat(statements).anyMatch { it.startsWith("create table $table", ignoreCase = true) }
        return statements
    }

    private fun seedUser(): List<String> =
        listOf(
            "INSERT INTO users (public_id, status, role, point_balance, is_phone_verified, warning_count, " +
                "is_pii_purged, created_at, updated_at) " +
                "VALUES ('01ABCDEF0123456789ABCDEF0', 'ACTIVE', 'GENERAL', 0, 0, 0, 0, NOW(6), NOW(6))",
        )

    private fun tableExists(
        url: String,
        table: String,
    ): Boolean =
        query(
            url,
            "SELECT COUNT(*) FROM information_schema.TABLES " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = '$table'",
        ) { it.getInt(1) }.single() > 0

    private fun columnsOf(
        url: String,
        table: String,
    ): Map<String, ColumnShape> =
        query(
            url,
            "SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT, EXTRA FROM information_schema.COLUMNS " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = '$table'",
        ) { rs ->
            rs.getString(1) to
                ColumnShape(
                    type = rs.getString(2).lowercase(),
                    nullable = rs.getString(3) == "YES",
                    default = rs.getString(4),
                    extra = rs.getString(5),
                )
        }.toMap()

    /** ddl-auto가 FK 때문에 만든 보조 인덱스(FK로 시작)는 비교에서 뺀다 — FK를 떼도 인덱스는 남는다. */
    private fun indexesOf(
        url: String,
        table: String,
    ): Map<String, String> =
        query(
            url,
            "SELECT INDEX_NAME, NON_UNIQUE, SEQ_IN_INDEX, COLUMN_NAME FROM information_schema.STATISTICS " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = '$table' ORDER BY INDEX_NAME, SEQ_IN_INDEX",
        ) { rs -> Triple(rs.getString(1), rs.getInt(2) == 0, rs.getString(4)) }
            .filterNot { it.first.startsWith("FK") }
            .groupBy { it.first }
            .mapValues { (_, rows) -> "${if (rows.first().second) "unique" else "index"}(${rows.joinToString(",") { it.third }})" }

    private fun migrationSql(): String {
        val stream = javaClass.classLoader.getResourceAsStream("db/migration/$MIGRATION_NAME")
        requireNotNull(stream) { "$MIGRATION_NAME 을 클래스패스에서 찾지 못했습니다" }
        return stream.use { it.reader().readText() }
    }

    private fun scalar(rs: ResultSet): Long =
        rs.use {
            it.next()
            it.getLong(1)
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

    /** 세션 변수와 PREPARE를 쓰는 마이그레이션이라 한 커넥션에서 순서대로 실행한다. */
    private fun execute(
        url: String,
        statements: List<String>,
    ) = connect(url).use { conn ->
        conn.createStatement().use { statement -> statements.forEach { statement.execute(it) } }
    }

    private fun statementsOf(sql: String): List<String> =
        sql
            .lineSequence()
            .filterNot { it.trimStart().startsWith("--") }
            .joinToString("\n")
            .split(";")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

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

    private data class ColumnShape(
        val type: String,
        val nullable: Boolean,
        val default: String?,
        val extra: String,
    )

    companion object {
        private const val MIGRATION_NAME = "V45__create_user_sanctions_and_users_sanction_until.sql"
        private const val SANCTIONS_TABLE = "user_sanctions"

        // MySQLContainer의 기본 root 계정. 테스트 전용 계정은 DB를 만들 권한이 없다.
        private const val ROOT_USER = "root"
        private const val ROOT_PASSWORD = "test"

        private val FOREIGN_KEY = Regex("foreign key", RegexOption.IGNORE_CASE)

        @JvmStatic
        @DynamicPropertySource
        fun containers(registry: DynamicPropertyRegistry) = IntegrationTestConfig.registerContainers(registry)
    }
}

private const val V45_ENTITY_SCHEMA_SCRIPT = "build/v45-entity-schema.sql"
