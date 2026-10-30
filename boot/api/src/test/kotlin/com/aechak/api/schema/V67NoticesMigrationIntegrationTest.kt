package com.aechak.api.schema

import com.aechak.api.support.IntegrationTestConfig
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.flywaydb.core.Flyway
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
import java.util.UUID
import javax.sql.DataSource

/**
 * V67(공지 테이블)의 안전성 검증.
 * 깨지면 "dev에 적용하면 공지 스키마가 엔티티와 갈린다" 또는 "재실행이 스키마를 바꾼다"는 뜻이다.
 *
 * 두 스키마를 대조한다.
 *  - fresh  : 빈 DB에 Flyway 전체 적용 — 기준
 *  - entity : Hibernate가 엔티티 선언대로 만들었을 DDL
 *
 * dev는 Flyway와 ddl-auto(update)를 함께 쓴다. 두 쪽이 갈리면 한 환경에만 있는 컬럼이나
 * 이름이 다른 인덱스가 생겨, 통합 테스트는 전부 녹색인데 운영 부팅이나 조회 계획에서만 터진다.
 */
@SpringBootTest(
    properties = [
        // DB를 건드리지 않고 Hibernate가 만들 DDL만 파일로 받는다.
        // 받을 파일 경로는 @DynamicPropertySource가 실행마다 새로 정한다 — 아래 ENTITY_SCHEMA_SCRIPT 주석 참조.
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.enabled=false",
        "spring.jpa.properties.jakarta.persistence.schema-generation.scripts.action=create",
        "spring.jpa.properties.hibernate.hbm2ddl.delimiter=;",
    ],
)
@Import(IntegrationTestConfig::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class V67NoticesMigrationIntegrationTest {
    @Autowired
    private lateinit var dataSource: DataSource

    private lateinit var freshUrl: String
    private lateinit var entityUrl: String

    @BeforeAll
    fun prepareSchemas() {
        freshUrl = recreateDatabase("v67_fresh")
        flyway(freshUrl).migrate()

        entityUrl = recreateDatabase("v67_entity")
        execute(entityUrl, entityStatementsOf(NOTICES_TABLE))
    }

    @Test
    fun `빈 DB에 Flyway 전체 적용이 성공하고 공지 테이블이 생긴다`() {
        val applied = query(freshUrl, "SELECT version FROM flyway_schema_history WHERE success = 1") { it.getString(1) }

        assertThat(applied).describedAs("성공으로 기록된 버전").contains("67")
        assertThat(tableExists(freshUrl, NOTICES_TABLE)).describedAs("$NOTICES_TABLE 존재").isTrue()
    }

    @Test
    fun `Flyway가 만든 공지 테이블이 엔티티 선언과 일치한다`() {
        val actual = columnsOf(freshUrl, NOTICES_TABLE)
        val expected = columnsOf(entityUrl, NOTICES_TABLE)

        assertThat(actual.keys).describedAs("컬럼 집합").isEqualTo(expected.keys)
        expected.forEach { (column, expectedShape) ->
            val actualShape = actual.getValue(column)
            assertThat(actualShape.nullable).describedAs("%s NULL 허용", column).isEqualTo(expectedShape.nullable)
            assertThat(actualShape.extra).describedAs("%s EXTRA", column).isEqualTo(expectedShape.extra)
            assertTypeCompatible(column, actualShape.type, expectedShape.type)
        }
        assertThat(indexesOf(freshUrl, NOTICES_TABLE))
            .describedAs("인덱스")
            .isEqualTo(indexesOf(entityUrl, NOTICES_TABLE))
    }

    @Test
    fun `V67을 다시 실행해도 스키마가 그대로다`() {
        val before = columnsOf(freshUrl, NOTICES_TABLE) to indexesOf(freshUrl, NOTICES_TABLE)

        execute(freshUrl, statementsOf(migrationSql()))

        assertThat(columnsOf(freshUrl, NOTICES_TABLE) to indexesOf(freshUrl, NOTICES_TABLE))
            .describedAs("재실행 뒤 스키마")
            .isEqualTo(before)
    }

    /**
     * 본문 상한은 애그리거트가 2000자로 막는데, 컬럼이 그만큼을 담지 못하면 그 검증을 통과한 공지가
     * 저장에서 깨진다. 한글은 utf8mb4에서 3바이트라 VARCHAR(2000)로는 바이트 한도에 걸릴 수 있다.
     */
    @Test
    fun `본문 컬럼이 한글 2000자를 그대로 담는다`() {
        val body = "가".repeat(2000)

        execute(freshUrl, listOf(insertNotice(publicId = LONG_BODY_PUBLIC_ID, content = body)))

        val stored =
            query(freshUrl, "SELECT CHAR_LENGTH(content) FROM notices WHERE public_id = '$LONG_BODY_PUBLIC_ID'") {
                it.getInt(1)
            }

        assertThat(stored).describedAs("저장된 본문 길이").containsExactly(2000)
    }

    /** publicId가 겹치면 다른 공지가 같은 경로로 열린다 — 유니크가 선언만 있고 실제로 막지 않으면 의미가 없다. */
    @Test
    fun `같은 public_id를 두 번 넣으면 거절된다`() {
        execute(freshUrl, listOf(insertNotice(publicId = DUPLICATE_PUBLIC_ID, content = "본문")))

        assertThatThrownBy { execute(freshUrl, listOf(insertNotice(publicId = DUPLICATE_PUBLIC_ID, content = "본문"))) }
            .describedAs("중복 public_id 삽입")
            .hasMessageContaining("Duplicate entry")
    }

    private fun insertNotice(
        publicId: String,
        content: String,
    ): String =
        "INSERT INTO notices (public_id, title, content, category, audience, status, pinned, " +
            "created_by_admin_id, created_at, updated_at) " +
            "VALUES ('$publicId', '제목', '$content', 'SERVICE', 'ALL', 'DRAFT', 0, 1, NOW(6), NOW(6))"

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
            statementsOf(ENTITY_SCHEMA_SCRIPT.readText())
                .filter { target.containsMatchIn(it) && !FOREIGN_KEY.containsMatchIn(it) }
                .sortedBy { if (it.startsWith("create table", ignoreCase = true)) 0 else 1 }
        assertThat(statements).anyMatch { it.startsWith("create table $table", ignoreCase = true) }
        return statements
    }

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
            .mapValues { (_, rows) ->
                "${if (rows.first().second) "unique" else "index"}(${rows.joinToString(",") { it.third }})"
            }

    private fun migrationSql(): String {
        val stream = javaClass.classLoader.getResourceAsStream("db/migration/$MIGRATION_NAME")
        requireNotNull(stream) { "$MIGRATION_NAME 을 클래스패스에서 찾지 못했습니다" }
        return stream.use { it.reader().readText() }
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

    private data class ColumnShape(
        val type: String,
        val nullable: Boolean,
        val default: String?,
        val extra: String,
    )

    companion object {
        private const val MIGRATION_NAME = "V67__create_notices.sql"
        private const val NOTICES_TABLE = "notices"
        private const val DUPLICATE_PUBLIC_ID = "01DUPLICATE000000000000001"
        private const val LONG_BODY_PUBLIC_ID = "01LONGBODY0000000000000001"

        // MySQLContainer의 기본 root 계정. 테스트 전용 계정은 DB를 만들 권한이 없다.
        private const val ROOT_USER = "root"
        private const val ROOT_PASSWORD = "test"

        private val FOREIGN_KEY = Regex("foreign key", RegexOption.IGNORE_CASE)

        /**
         * Hibernate가 만들 DDL을 받을 파일. 실행마다 새 경로를 쓴다 —
         * Hibernate는 고정 경로에 **이어 쓰기** 때문에, 엔티티 선언이 바뀌면 옛 CREATE TABLE과
         * 새 CREATE TABLE이 한 파일에 쌓인다. 그러면 둘 다 실행되어 "이미 있는 테이블"로 깨지거나,
         * 이번 실행이 DDL을 쓰지 못했는데도 지난 실행의 파일로 비교가 통과한다.
         */
        private val ENTITY_SCHEMA_SCRIPT: File =
            File("build/v67-entity-schema-${UUID.randomUUID()}.sql").also {
                it.parentFile.mkdirs()
                it.delete()
                it.deleteOnExit()
            }

        @JvmStatic
        @DynamicPropertySource
        fun schemaScript(registry: DynamicPropertyRegistry) {
            IntegrationTestConfig.registerContainers(registry)
            registry.add("spring.jpa.properties.jakarta.persistence.schema-generation.scripts.create-target") {
                ENTITY_SCHEMA_SCRIPT.absolutePath
            }
        }
    }
}
