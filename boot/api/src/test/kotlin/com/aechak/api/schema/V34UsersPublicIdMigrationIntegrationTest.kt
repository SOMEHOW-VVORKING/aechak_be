package com.aechak.api.schema

import com.aechak.api.support.IntegrationTestConfig
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
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
import java.sql.SQLException
import javax.sql.DataSource

/**
 * V34(users.public_id 추가와 기존 사용자 채우기)의 안전성 검증.
 * 깨지면 기존 사용자의 공개 ID가 겹치거나 비거나, 재실행이 이미 공유된 값을 바꾼다는 뜻이다.
 *
 * legacy 스키마는 ddl-auto가 만든 V34 직전 users(public_id 없음)에 여러 행을 넣은 뒤 Flyway를 끝까지 적용한다.
 */
@SpringBootTest(
    properties = [
        // DB를 건드리지 않고 Hibernate가 만들 DDL만 파일로 받는다.
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.enabled=false",
        "spring.jpa.properties.jakarta.persistence.schema-generation.scripts.action=create",
        "spring.jpa.properties.jakarta.persistence.schema-generation.scripts.create-target=$ENTITY_SCHEMA_SCRIPT",
        "spring.jpa.properties.hibernate.hbm2ddl.delimiter=;",
    ],
)
@Import(IntegrationTestConfig::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class V34UsersPublicIdMigrationIntegrationTest {
    @Autowired
    private lateinit var dataSource: DataSource

    private lateinit var legacyUrl: String

    @BeforeAll
    fun prepareLegacy() {
        legacyUrl = recreateDatabase("v34_legacy")
        migrateUpTo(legacyUrl, "32")
        execute(legacyUrl, preV34UsersStatements() + seedUsers(LEGACY_USER_COUNT))
        flyway(legacyUrl).migrate()
    }

    @Test
    fun `기존 사용자 모두가 서로 다른 26자 public_id를 받는다`() {
        val ids = publicIds(legacyUrl)

        assertThat(ids).describedAs("기존 사용자 수").hasSize(LEGACY_USER_COUNT)
        assertThat(ids.values.toSet()).describedAs("겹치지 않는 public_id 수").hasSize(LEGACY_USER_COUNT)
        assertThat(ids.values).describedAs("채운 값 형식").allSatisfy { assertThat(it).matches(PUBLIC_ID_FORMAT) }
    }

    @Test
    fun `public_id에 NOT NULL과 UNIQUE가 걸린다`() {
        val nullable =
            query(
                legacyUrl,
                "SELECT IS_NULLABLE FROM information_schema.COLUMNS " +
                    "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND COLUMN_NAME = 'public_id'",
            ) { it.getString(1) }
        assertThat(nullable).describedAs("public_id NULL 허용 여부").containsExactly("NO")

        val unique =
            query(
                legacyUrl,
                "SELECT NON_UNIQUE, COLUMN_NAME FROM information_schema.STATISTICS " +
                    "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND INDEX_NAME = 'uk_users_public_id'",
            ) { "${it.getInt(1)}/${it.getString(2)}" }
        assertThat(unique).describedAs("uk_users_public_id가 public_id 하나에 걸린 UNIQUE").containsExactly("0/public_id")
    }

    @Test
    fun `적용 뒤 public_id 없이 넣은 행도 값을 받는다`() {
        // 엔티티 매핑이 바뀌기 전의 모든 모듈이 이 모양으로 가입 행을 넣는다.
        val url = recreateDatabase("v34_insert")
        migrateUpTo(url, "32")
        execute(url, preV34UsersStatements())
        flyway(url).migrate()

        execute(url, seedUsers(2))

        assertFilled(url, 2)
    }

    @Test
    fun `채우기와 NOT NULL 변경 사이에 public_id 없이 들어온 가입 행도 기본값을 받아 마이그레이션이 끝난다`() {
        val url = recreateDatabase("v34_between")
        migrateUpTo(url, "32")
        execute(url, preV34UsersStatements() + seedUsers(3))
        val statements = statementsOf(migrationSql())
        val notNullAt = statements.indexOfFirst { it.contains("MODIFY COLUMN public_id") }

        connect(url).use { conn ->
            conn.createStatement().use { statement ->
                statements.take(notNullAt).forEach { statement.execute(it) }
                seedUsers(1).forEach { statement.execute(it) }
                statements.drop(notNullAt).forEach { statement.execute(it) }
            }
        }

        assertFilled(url, 4)
    }

    @Test
    fun `다시 실행해도 실패하지 않고 채운 값을 바꾸지 않으며 세션 설정을 되돌린다`() {
        val before = publicIds(legacyUrl)

        val (lockWaitTimeout, sqlMode) =
            connect(legacyUrl).use { conn ->
                conn.createStatement().use { statement ->
                    // 비strict에서 시작해야 STRICT_ALL_TABLES가 남는지 구분된다.
                    statement.execute("SET SESSION sql_mode = ''")
                    val original = scalar(statement.executeQuery("SELECT @@SESSION.lock_wait_timeout"))
                    statementsOf(migrationSql()).forEach { statement.execute(it) }
                    val restoredSqlMode =
                        statement.executeQuery("SELECT @@SESSION.sql_mode").use {
                            it.next()
                            it.getString(1)
                        }
                    (original to scalar(statement.executeQuery("SELECT @@SESSION.lock_wait_timeout"))) to restoredSqlMode
                }
            }

        assertThat(publicIds(legacyUrl)).describedAs("재실행 뒤 public_id").isEqualTo(before)
        assertThat(lockWaitTimeout.second).describedAs("재실행 뒤 lock_wait_timeout").isEqualTo(lockWaitTimeout.first)
        assertThat(sqlMode).describedAs("재실행 뒤 sql_mode").isEmpty()
    }

    @Test
    fun `NOT NULL 변경 전에 NULL 행이 남으면 비strict 세션에서도 빈 문자열로 바꾸지 않고 실패하며 다시 실행하면 끝난다`() {
        val url = recreateDatabase("v34_nonstrict")
        migrateUpTo(url, "32")
        execute(url, preV34UsersStatements() + seedUsers(3))
        val statements = statementsOf(migrationSql())
        val notNullAt = statements.indexOfFirst { it.contains("MODIFY COLUMN public_id") }

        connect(url).use { conn ->
            conn.createStatement().use { statement ->
                statement.execute("SET SESSION sql_mode = ''")
                statements.take(notNullAt).forEach { statement.execute(it) }
                // 채우기와 NOT NULL 변경 사이에 NULL을 직접 넣는 행
                statement.execute(
                    "INSERT INTO users (public_id, status, role, point_balance, is_phone_verified, warning_count, " +
                        "is_pii_purged, created_at, updated_at) VALUES (NULL, 'ACTIVE', 'GENERAL', 0, 0, 0, 0, NOW(6), NOW(6))",
                )
                // 1138(Invalid use of NULL value)이어야 strict가 걸린 것이다. strict가 없으면 INPLACE가 1846으로 실패한다.
                assertThatThrownBy { statements.drop(notNullAt).forEach { statement.execute(it) } }
                    .describedAs("남은 NULL 행 때문에 NOT NULL 변경이 실패한 오류")
                    .isInstanceOfSatisfying(SQLException::class.java) { assertThat(it.errorCode).isEqualTo(1138) }
            }
        }
        assertThat(query(url, "SELECT COUNT(*) FROM users WHERE public_id IS NULL") { it.getInt(1) })
            .describedAs("실패 뒤 NULL로 남은 행 수")
            .containsExactly(1)
        assertThat(query(url, "SELECT COUNT(*) FROM users WHERE public_id = ''") { it.getInt(1) })
            .describedAs("빈 문자열로 바뀐 행 수")
            .containsExactly(0)

        execute(url, statements)

        assertFilled(url, 4)
    }

    @Test
    fun `users가 없는 빈 DB에서는 아무 일도 하지 않고 성공한다`() {
        val url = recreateDatabase("v34_fresh")

        flyway(url).migrate()

        val applied =
            query(url, "SELECT version FROM flyway_schema_history WHERE success = 1") { it.getString(1) }
        assertThat(applied).describedAs("성공으로 기록된 버전").contains("34")
        val users =
            query(
                url,
                "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users'",
            ) { it.getInt(1) }
        assertThat(users).describedAs("빈 DB에 생긴 users 테이블 수").containsExactly(0)
    }

    private fun assertFilled(
        url: String,
        count: Int,
    ) {
        val ids = publicIds(url).values
        assertThat(ids).describedAs("public_id 수와 중복").hasSize(count).doesNotHaveDuplicates()
        assertThat(ids).describedAs("public_id 형식").allSatisfy { assertThat(it).matches(PUBLIC_ID_FORMAT) }
    }

    /** ddl-auto가 V34 직전까지 만들어 둔 users. 엔티티 선언대로 만들되 FK는 뺀다. */
    private fun preV34UsersStatements(): List<String> {
        val statements =
            statementsOf(File(ENTITY_SCHEMA_SCRIPT).readText())
                .filter { USERS_TABLE.containsMatchIn(it) && !FOREIGN_KEY.containsMatchIn(it) }
                .distinct() // Hibernate가 실행마다 스크립트 파일에 이어 쓴다
        assertThat(statements).anyMatch { it.startsWith("create table users", ignoreCase = true) }
        assertThat(statements).noneMatch { it.contains("public_id") }
        return statements
    }

    private fun seedUsers(count: Int): List<String> =
        listOf(
            """
            INSERT INTO users (status, role, point_balance, is_phone_verified, warning_count, is_pii_purged,
                               created_at, updated_at)
            WITH RECURSIVE seq(n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < $count)
            SELECT 'ACTIVE', 'GENERAL', 0, 0, 0, 0, NOW(6), NOW(6) FROM seq
            """.trimIndent(),
        )

    private fun publicIds(url: String): Map<Long, String> =
        query(url, "SELECT id, public_id FROM users") { it.getLong(1) to it.getString(2) }.toMap()

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
        conn.createStatement().use { statement ->
            statements.forEach { statement.execute(it) }
        }
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

    companion object {
        private const val MIGRATION_NAME = "V34__add_users_public_id.sql"

        // MySQLContainer의 기본 root 계정. 테스트 전용 계정은 DB를 만들 권한이 없다.
        private const val ROOT_USER = "root"
        private const val ROOT_PASSWORD = "test"

        private const val LEGACY_USER_COUNT = 1000

        private val PUBLIC_ID_FORMAT = Regex("^0[0-9A-F]{25}$").toPattern()
        private val USERS_TABLE = Regex("^(create|alter) table (?:if exists )?`?users`?\\s", RegexOption.IGNORE_CASE)
        private val FOREIGN_KEY = Regex("foreign key", RegexOption.IGNORE_CASE)

        @JvmStatic
        @DynamicPropertySource
        fun containers(registry: DynamicPropertyRegistry) = IntegrationTestConfig.registerContainers(registry)
    }
}

private const val ENTITY_SCHEMA_SCRIPT = "build/v34-entity-schema.sql"
