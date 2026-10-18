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
import java.sql.DriverManager
import javax.sql.DataSource

/**
 * V27(ddl-auto 의존 테이블 Flyway 이관)의 안전성 검증.
 *
 * 세 개의 스키마를 컨테이너에 따로 만들어 비교한다.
 *  - fresh   : 빈 DB에 Flyway 전체 적용
 *  - entity  : Hibernate가 엔티티 선언대로 만들었을 DDL(스크립트 생성으로 받아 적용)
 *  - legacy  : dev 재현 — V26까지 적용된 DB에 Hibernate가 테이블을 만들어 둔 상태에서 V27 적용
 *
 * ddl-auto=validate 부팅으로는 이 정합을 볼 수 없다. 아직 Flyway 밖에 있는 테이블(users·products 등
 * 20여 개)이 빈 DB에 없어 validate가 그 테이블들에서 먼저 깨지기 때문이다. 그래서 이관 대상 테이블만
 * 떼어 "엔티티 선언 ↔ 마이그레이션" 을 직접 비교한다.
 */
@SpringBootTest(
    properties = [
        // DB를 건드리지 않고 Hibernate가 만들 DDL만 파일로 받는다 — 공용 DB 상태와 무관하게 비교 대상을 얻는다.
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.enabled=false",
        "spring.jpa.properties.jakarta.persistence.schema-generation.scripts.action=create",
        "spring.jpa.properties.jakarta.persistence.schema-generation.scripts.create-target=build/v27-entity-schema.sql",
        "spring.jpa.properties.hibernate.hbm2ddl.delimiter=;",
    ],
)
@Import(IntegrationTestConfig::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class V27SchemaMigrationIntegrationTest {
    @Autowired
    private lateinit var dataSource: DataSource

    private lateinit var freshUrl: String
    private lateinit var entityUrl: String
    private lateinit var legacyUrl: String

    @BeforeAll
    fun prepareSchemas() {
        freshUrl = recreateDatabase("v27_fresh")
        flyway(freshUrl).migrate()

        entityUrl = recreateDatabase("v27_entity")
        execute(entityUrl, entitySchemaStatements(TARGET_TABLES))

        // dev의 시간 순서를 그대로 재현한다 — ddl-auto가 테이블을 먼저 만들고, 그 위로 마이그레이션이 쌓였다.
        legacyUrl = recreateDatabase("v27_legacy")
        migrateUpTo(legacyUrl, "1")
        execute(legacyUrl, legacyPreV27Statements())
        migrateUpTo(legacyUrl, "26")
        execute(legacyUrl, legacyForeignKeyStatements())
        flyway(legacyUrl).migrate()
    }

    @Test
    fun `빈 DB에 Flyway 전체 적용이 성공하고 택배사가 시드된다`() {
        val applied =
            query(freshUrl, "SELECT version FROM flyway_schema_history WHERE success = 1") { it.getString(1) }
        assertThat(applied).contains("27")

        val couriers = query(freshUrl, "SELECT courier_code FROM courier_masters ORDER BY courier_code") { it.getString(1) }
        assertThat(couriers).containsExactly("CJ", "HANJIN", "LOGEN", "LOTTE", "POST")

        val inactive = query(freshUrl, "SELECT courier_code FROM courier_masters WHERE is_active = b'0'") { it.getString(1) }
        assertThat(inactive).isEmpty()
    }

    @Test
    fun `V27을 다시 실행해도 스키마와 시드가 늘지 않는다`() {
        val before = TARGET_TABLES.associateWith { snapshot(freshUrl, it) }

        execute(freshUrl, statementsOf(migrationSql()))

        assertThat(TARGET_TABLES.associateWith { snapshot(freshUrl, it) }).isEqualTo(before)
        val couriers = query(freshUrl, "SELECT courier_code FROM courier_masters") { it.getString(1) }
        assertThat(couriers).hasSize(5)
    }

    @Test
    fun `유저당 신청 1행 제약은 엔티티보다 DB가 더 좁다`() {
        // V10이 기존 DB에 걸어 둔 결정(행 재사용 모델의 방어선). 엔티티 선언은 nullable이라 비교에서 예외로 둔다.
        val nullable = snapshot(freshUrl, "seller_applications").columns.getValue("user_id").nullable
        assertThat(nullable).isFalse()
        assertThat(snapshot(legacyUrl, "seller_applications").columns.getValue("user_id").nullable).isFalse()
    }

    @Test
    fun `Flyway가 만든 스키마가 엔티티 선언과 일치한다`() {
        TARGET_TABLES.forEach { table ->
            val fromFlyway = snapshot(freshUrl, table)
            val fromEntity = snapshot(entityUrl, table)

            assertThat(fromFlyway.columns.keys)
                .describedAs("%s 컬럼 집합", table)
                .isEqualTo(fromEntity.columns.keys)
            fromEntity.columns.forEach { (column, entityShape) ->
                assertColumnMatches(table, column, fromFlyway.columns.getValue(column), entityShape)
            }
            assertThat(fromFlyway.indexes)
                .describedAs("%s 인덱스", table)
                .isEqualTo(fromEntity.indexes)
        }
    }

    @Test
    fun `ddl-auto가 만든 테이블이 있는 DB에 적용해도 빈 DB와 같은 모양이 된다`() {
        TARGET_TABLES.forEach { table ->
            val fromFlyway = snapshot(freshUrl, table)
            val afterMigration = snapshot(legacyUrl, table)

            assertThat(afterMigration.columns.keys)
                .describedAs("%s 컬럼 집합", table)
                .isEqualTo(fromFlyway.columns.keys)
            fromFlyway.columns.forEach { (column, freshShape) ->
                assertColumnMatches(table, column, afterMigration.columns.getValue(column), freshShape)
            }
            assertThat(afterMigration.indexes)
                .describedAs("%s 인덱스", table)
                .isEqualTo(fromFlyway.indexes)
        }
    }

    /**
     * enum 컬럼만 예외를 둔다. Hibernate 7은 MySQL 네이티브 enum으로 만들지만 이 레포의 마이그레이션은
     * V21·V24와 같은 규약으로 VARCHAR을 쓴다 — 값 집합이 코드에 있고 enum 값 추가가 DDL 변경이 되는 것을
     * 피하기 위한 기존 선택이라 여기서 뒤집지 않는다.
     */
    private fun assertColumnMatches(
        table: String,
        column: String,
        actual: ColumnShape,
        expected: ColumnShape,
    ) {
        if ("$table.$column" !in DB_STRICTER_THAN_ENTITY) {
            assertThat(actual.nullable)
                .describedAs("%s.%s NULL 허용", table, column)
                .isEqualTo(expected.nullable)
        }
        assertThat(actual.extra)
            .describedAs("%s.%s EXTRA", table, column)
            .isEqualTo(expected.extra)
        if (actual.type != expected.type) {
            assertThat(setOf(actual.type.substringBefore("("), expected.type.substringBefore("(")))
                .describedAs("%s.%s 타입 (%s vs %s)", table, column, actual.type, expected.type)
                .isEqualTo(setOf("enum", "varchar"))
        }
    }

    /**
     * Hibernate가 생성한 스크립트에서 주어진 테이블을 건드리는 문장만 원래 순서로 고른다.
     * 같은 내용이 중복돼 있으면(스크립트가 이어 붙는 경우) 한 번만 쓴다.
     *
     * FK는 뺀다. 이 레포의 마이그레이션은 FK를 만들지 않는데(V21·V23 규약) 엔티티 연관 선언에는 FK가 남아 있어
     * ddl-auto 환경에만 FK와 그 보조 인덱스가 생긴다 — 그 차이는 이 PR의 범위가 아니라 비교에서 제외한다.
     */
    private fun entitySchemaStatements(tables: List<String>): List<String> {
        val picked = linkedSetOf<String>()
        statementsOf(File(ENTITY_SCHEMA_SCRIPT).readText()).forEach { statement ->
            if (targetTableOf(statement) in tables && !FOREIGN_KEY.containsMatchIn(statement)) picked += statement
        }
        val created = picked.mapNotNull { CREATE_TABLE.find(it)?.groupValues?.get(1) }
        assertThat(created).containsExactlyInAnyOrderElementsOf(tables)
        return picked.toList()
    }

    /** ddl-auto가 V27 직전까지 만들어 두었던 모양 — 이 PR이 추가하는 인덱스·컬럼이 아직 없다. */
    private fun legacyPreV27Statements(): List<String> =
        entitySchemaStatements(DDL_AUTO_TABLES)
            .filterNot { statement -> ADDED_INDEXES.any { statement.contains(it) } } +
            listOf(
                "ALTER TABLE claims DROP COLUMN seller_id",
                "ALTER TABLE claims DROP COLUMN order_id",
                "ALTER TABLE claims DROP COLUMN seller_memo",
            )

    /** ddl-auto가 걸어 둔 FK를 흉내낸다 — FK와 그 보조 인덱스가 있어도 조건부 ALTER가 걸리지 않아야 한다. */
    private fun legacyForeignKeyStatements(): List<String> =
        listOf(
            "ALTER TABLE shipments ADD CONSTRAINT FKlegacyshipmentsorder FOREIGN KEY (order_id) REFERENCES orders (id)",
            "ALTER TABLE claim_items ADD CONSTRAINT FKlegacyclaimitemsclaim FOREIGN KEY (claim_id) REFERENCES claims (id)",
            "ALTER TABLE order_status_histories ADD CONSTRAINT FKlegacyoshorder FOREIGN KEY (order_id) REFERENCES orders (id)",
        )

    private fun targetTableOf(statement: String): String? =
        CREATE_TABLE.find(statement)?.groupValues?.get(1)
            ?: CREATE_INDEX.find(statement)?.groupValues?.get(1)
            ?: ALTER_TABLE.find(statement)?.groupValues?.get(1)

    private fun migrationSql(): String {
        val stream = javaClass.classLoader.getResourceAsStream("db/migration/$MIGRATION_NAME")
        requireNotNull(stream) { "$MIGRATION_NAME 을 클래스패스에서 찾지 못했습니다" }
        return stream.use { it.reader().readText() }
    }

    private fun snapshot(
        url: String,
        table: String,
    ): TableShape = TableShape(columnsOf(url, table), indexesOf(url, table))

    private fun columnsOf(
        url: String,
        table: String,
    ): Map<String, ColumnShape> =
        query(
            url,
            """
            SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, EXTRA
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = '$table'
            """.trimIndent(),
        ) { rs ->
            rs.getString(1) to ColumnShape(rs.getString(2).lowercase(), rs.getString(3) == "YES", rs.getString(4))
        }.toMap()

    /** ddl-auto가 FK 때문에 만든 보조 인덱스(FK로 시작)는 비교에서 뺀다 — FK를 쓰지 않기로 한 규약과 무관한 잔재다. */
    private fun indexesOf(
        url: String,
        table: String,
    ): Map<String, String> =
        query(
            url,
            """
            SELECT INDEX_NAME, NON_UNIQUE, SEQ_IN_INDEX, COLUMN_NAME
            FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = '$table'
            ORDER BY INDEX_NAME, SEQ_IN_INDEX
            """.trimIndent(),
        ) { rs -> Triple(rs.getString(1), rs.getInt(2) == 0, rs.getString(4)) }
            .filterNot { it.first.startsWith("FK") }
            .groupBy { it.first }
            .mapValues { (_, rows) ->
                val kind = if (rows.first().second) "unique" else "index"
                "$kind(${rows.joinToString(", ") { it.third }})"
            }

    private fun <T> query(
        url: String,
        sql: String,
        row: (java.sql.ResultSet) -> T,
    ): List<T> =
        connect(url).use { conn ->
            conn.createStatement().use { statement ->
                val rs = statement.executeQuery(sql)
                val result = mutableListOf<T>()
                while (rs.next()) result += row(rs)
                result
            }
        }

    /** PREPARE/EXECUTE 와 세션 변수를 쓰는 마이그레이션이라 한 커넥션에서 순서대로 흘려야 한다. */
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

    private fun connect(url: String): Connection = DriverManager.getConnection(url, ROOT_USER, ROOT_PASSWORD)

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
        val extra: String,
    )

    private data class TableShape(
        val columns: Map<String, ColumnShape>,
        val indexes: Map<String, String>,
    )

    companion object {
        private const val MIGRATION_NAME = "V27__migrate_ddl_auto_tables.sql"

        /** 엔티티 선언보다 DB가 더 좁은 컬럼 — 비교에서 NULL 허용만 예외로 둔다. */
        private val DB_STRICTER_THAN_ENTITY = setOf("seller_applications.user_id")
        private const val ENTITY_SCHEMA_SCRIPT = "build/v27-entity-schema.sql"

        // MySQLContainer의 기본 root 계정. 테스트 전용 계정은 DB를 만들 권한이 없다.
        private const val ROOT_USER = "root"
        private const val ROOT_PASSWORD = "test"

        private val CREATE_TABLE = Regex("^create table (?:if not exists )?`?([a-z_0-9]+)`?", RegexOption.IGNORE_CASE)
        private val CREATE_INDEX = Regex("^create index \\S+ on `?([a-z_0-9]+)`?", RegexOption.IGNORE_CASE)
        private val ALTER_TABLE = Regex("^alter table (?:if exists )?`?([a-z_0-9]+)`?", RegexOption.IGNORE_CASE)
        private val FOREIGN_KEY = Regex("foreign key", RegexOption.IGNORE_CASE)

        /** 이 PR이 새로 붙이는 인덱스 — legacy 재현에서 빼야 한다. */
        private val ADDED_INDEXES =
            listOf(
                "idx_application_reviews_application_id",
                "idx_claims_seller_id_status",
                "idx_claims_order_id",
                "idx_claims_auto_approve_due_at",
                "idx_claim_items_claim_id",
                "idx_claim_items_order_item_id",
                "idx_shipments_order_id",
                "idx_shipments_claim_id",
                "idx_shipments_dispatched_at",
                "idx_order_status_histories_order_id",
            )

        private val TARGET_TABLES =
            listOf(
                "sellers",
                "seller_applications",
                "application_documents",
                "application_reviews",
                "settlement_accounts",
                "order_status_histories",
                "courier_masters",
                "shipments",
                "claims",
                "claim_items",
                "orders",
            )

        /** orders는 V21이 이미 만든다 — ddl-auto만으로 존재하던 테이블은 나머지 10개다. */
        private val DDL_AUTO_TABLES = TARGET_TABLES - "orders"

        @JvmStatic
        @DynamicPropertySource
        fun containers(registry: DynamicPropertyRegistry) = IntegrationTestConfig.registerContainers(registry)
    }
}
