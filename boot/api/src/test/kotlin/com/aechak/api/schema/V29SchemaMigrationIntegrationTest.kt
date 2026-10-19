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
import javax.sql.DataSource

/**
 * V29(정산 테이블 Flyway 이관 + 수수료율 정책 신설)의 안전성 검증.
 * 깨지면 "dev에 적용하면 정산 스키마가 갈린다" 또는 "요율 시드가 운영 값을 덮는다"는 뜻이다.
 *
 * 세 개의 스키마를 컨테이너에 따로 만들어 대조한다.
 *  - fresh  : 빈 DB에 Flyway 전체 적용 — 기준
 *  - entity : Hibernate가 엔티티 선언대로 만들었을 DDL(스키마 생성 스크립트로 받아 적용)
 *  - legacy : dev 재현 — ddl-auto가 보강 컬럼·인덱스 없이 테이블을 만들고 FK까지 걸어 둔 DB + 기존 정산 1행
 */
@SpringBootTest(
    properties = [
        // DB를 건드리지 않고 Hibernate가 만들 DDL만 파일로 받는다 — 공용 DB 상태와 무관하게 비교 대상을 얻는다.
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.enabled=false",
        "spring.jpa.properties.jakarta.persistence.schema-generation.scripts.action=create",
        "spring.jpa.properties.jakarta.persistence.schema-generation.scripts.create-target=build/v29-entity-schema.sql",
        "spring.jpa.properties.hibernate.hbm2ddl.delimiter=;",
    ],
)
@Import(IntegrationTestConfig::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class V29SchemaMigrationIntegrationTest {
    @Autowired
    private lateinit var dataSource: DataSource

    private lateinit var freshUrl: String
    private lateinit var entityUrl: String
    private lateinit var legacyUrl: String

    @BeforeAll
    fun prepareSchemas() {
        freshUrl = recreateDatabase("v29_fresh")
        flyway(freshUrl).migrate()

        entityUrl = recreateDatabase("v29_entity")
        execute(entityUrl, entitySchemaStatements(TARGET_TABLES))

        legacyUrl = buildDriftSchema("v29_legacy", rows = legacySettlementRows())
    }

    /** ddl-auto가 V29 직전까지 만들어 둔 모양을 재현한 뒤 V29까지 적용한다. */
    private fun buildDriftSchema(
        name: String,
        rows: List<String>,
        extraStatements: List<String> = emptyList(),
        migrateToLatest: Boolean = true,
    ): String {
        val url = recreateDatabase(name)
        migrateUpTo(url, "28")
        execute(url, preV29DriftStatements() + rows + extraStatements + driftForeignKeyStatements())
        if (migrateToLatest) flyway(url).migrate()
        return url
    }

    @Test
    fun `빈 DB에 Flyway 전체 적용이 성공하고 전역 요율 정책이 한 행 심긴다`() {
        val applied = query(freshUrl, "SELECT version FROM flyway_schema_history WHERE success = 1") { it.getString(1) }
        assertThat(applied).contains("29")

        val policies =
            query(
                freshUrl,
                "SELECT seller_id, effective_from, platform_fee_rate, pg_fee_rate FROM settlement_fee_policies",
            ) { "${it.getObject(1)}/${it.getString(2)}/${it.getString(3)}/${it.getString(4)}" }
        assertThat(policies).containsExactly("null/2026-01-01/0.0800/0.0280")
    }

    @Test
    fun `V29를 다시 실행해도 스키마가 그대로다`() {
        val before = TARGET_TABLES.associateWith { snapshot(freshUrl, it) }

        execute(freshUrl, statementsOf(migrationSql()))

        assertThat(TARGET_TABLES.associateWith { snapshot(freshUrl, it) }).isEqualTo(before)
    }

    @Test
    fun `요율 시드 재실행은 운영에서 바꾼 요율을 되돌리지 않는다`() {
        // 운영 데이터를 바꿔 보는 테스트라 기준 스키마(fresh)를 더럽히지 않게 전용 DB를 쓴다.
        val seedUrl = recreateDatabase("v29_seed")
        flyway(seedUrl).migrate()
        execute(seedUrl, listOf("UPDATE settlement_fee_policies SET platform_fee_rate = 0.0600 WHERE seller_id IS NULL"))

        execute(seedUrl, statementsOf(migrationSql()))

        val rates =
            query(seedUrl, "SELECT platform_fee_rate FROM settlement_fee_policies WHERE seller_id IS NULL") { it.getString(1) }
        assertThat(rates).containsExactly("0.0600")
    }

    @Test
    fun `Flyway가 만든 스키마가 엔티티 선언과 일치한다`() {
        assertSameShape(actualUrl = freshUrl, expectedUrl = entityUrl, tolerateNativeEnum = true)
    }

    @Test
    fun `ddl-auto가 만든 DB에 적용해도 빈 DB와 같은 모양이 된다`() {
        assertSameShape(actualUrl = legacyUrl, expectedUrl = freshUrl, tolerateNativeEnum = false)
    }

    @Test
    fun `기존 정산 행의 보강 컬럼은 주문 경로와 전역 정책으로 채워진다`() {
        val filled =
            query(
                legacyUrl,
                """
                SELECT order_id, shipping_fee, refund_deduction, platform_fee_rate, pg_fee_rate,
                       settlement_month, expected_payout_date
                FROM settlements WHERE id = $SEEDED_SETTLEMENT_ID
                """.trimIndent(),
            ) {
                listOf(
                    it.getLong(1).toString(),
                    it.getLong(2).toString(),
                    it.getLong(3).toString(),
                    it.getString(4),
                    it.getString(5),
                    it.getString(6),
                    it.getString(7),
                )
            }

        assertThat(filled).hasSize(1)
        val row = filled.first()
        assertThat(row.subList(0, 5)).containsExactly(SEEDED_ORDER_ID.toString(), "0", "0", "0.0800", "0.0280")
        // 정산월·지급 예정일은 저장된 생성 시각에서 뽑으므로 값 자체가 아니라 채워졌다는 것과 그 관계를 본다.
        assertThat(row[5]).matches("\\d{4}-\\d{2}")
        assertThat(row[6]).matches("\\d{4}-\\d{2}-\\d{2}")
    }

    @Test
    fun `정산 상태 컬럼이 VARCHAR로 수렴해 보류 상태를 쓸 수 있다`() {
        listOf(freshUrl, legacyUrl).forEach { url ->
            assertThat(snapshot(url, "settlements").columns.getValue("status").type).isEqualTo("varchar(30)")
        }

        // 네이티브 enum으로 남아 있으면 이 INSERT가 깨진다 — ON_HOLD는 이 PR이 새로 더한 값이다.
        execute(
            legacyUrl,
            listOf(
                """
                INSERT INTO settlements (payout_id, seller_id, order_id, order_item_id, gross_amount, shipping_fee,
                                         commission, pg_fee, seller_coupon_burden, refund_deduction, net_amount,
                                         platform_fee_rate, pg_fee_rate, settlement_month, expected_payout_date,
                                         status, idempotency_key, created_at, updated_at)
                VALUES (NULL, $SEEDED_SELLER_ID, $SEEDED_ORDER_ID, $SEEDED_ORDER_ITEM_ID, 10000, 0, 800, 280, 0, 0, 8920,
                        0.0800, 0.0280, '2026-03', '2026-03-15', 'ON_HOLD', 'SETTLE:ORDER_ITEM:ON_HOLD', NOW(6), NOW(6))
                """.trimIndent(),
            ),
        )

        val held =
            query(legacyUrl, "SELECT status FROM settlements WHERE idempotency_key = 'SETTLE:ORDER_ITEM:ON_HOLD'") { it.getString(1) }
        assertThat(held).containsExactly("ON_HOLD")
    }

    @Test
    fun `ddl-auto가 걸어 둔 외래키는 제거된다`() {
        listOf("settlements", "payouts").forEach { table ->
            assertThat(foreignKeysOf(legacyUrl, table)).describedAs("%s 외래키", table).isEmpty()
        }
    }

    @Test
    fun `같은 기준일로 집행 배치를 두 번 편성할 수 없다`() {
        val insert = { suffix: String ->
            listOf(
                "INSERT INTO settlement_execution_batches (settlement_base_date, total_target_count, total_amount, " +
                    "status, created_at, updated_at) VALUES ('2026-03-20', 1, 100$suffix, 'GENERATED', NOW(6), NOW(6))",
            )
        }
        execute(freshUrl, insert(""))

        assertThatThrownBy { execute(freshUrl, insert("0")) }
            .hasMessageContaining("uk_settlement_execution_batches_base_date")
    }

    @Test
    fun `이미 채워진 요율 스냅샷은 백필이 덮지 않는다`() {
        // 보강 컬럼 하나만 먼저 생겨 값이 든 DB — 두 요율을 한꺼번에 대입하면 확정된 스냅샷이 전역 정책으로 바뀐다.
        val partialUrl =
            buildDriftSchema(
                "v29_partial",
                rows = legacySettlementRows(),
                extraStatements =
                    listOf(
                        "ALTER TABLE settlements ADD COLUMN platform_fee_rate DECIMAL(5, 4) NULL",
                        "UPDATE settlements SET platform_fee_rate = 0.0500 WHERE id = $SEEDED_SETTLEMENT_ID",
                    ),
            )

        val rates =
            query(
                partialUrl,
                "SELECT platform_fee_rate, pg_fee_rate FROM settlements WHERE id = $SEEDED_SETTLEMENT_ID",
            ) { it.getString(1) to it.getString(2) }

        assertThat(rates).containsExactly("0.0500" to "0.0280")
        assertSameShape(actualUrl = partialUrl, expectedUrl = freshUrl, tolerateNativeEnum = false)
    }

    @Test
    fun `주문으로 이어지지 않는 정산 행이 있으면 적용이 실패한다`() {
        val orphanUrl =
            buildDriftSchema("v29_orphan", rows = legacySettlementRows() + orphanSettlementRow(), migrateToLatest = false)

        // 백필이 채우지 못한 행이 남아 NOT NULL 좁히기가 막힌다 — 조용히 0을 넣지 않는다는 뜻이다.
        assertThatThrownBy { flyway(orphanUrl).migrate() }
            .hasMessageContaining("V29__create_settlement_tables_and_fee_policy.sql")
            .hasMessageContaining("Invalid use of NULL value")
    }

    /** 컬럼(이름·타입·NULL 허용·기본값·EXTRA)과 명명 인덱스(유일성·컬럼 순서)를 테이블 단위로 대조한다. */
    private fun assertSameShape(
        actualUrl: String,
        expectedUrl: String,
        tolerateNativeEnum: Boolean,
    ) {
        TARGET_TABLES.forEach { table ->
            val actual = snapshot(actualUrl, table)
            val expected = snapshot(expectedUrl, table)

            assertThat(actual.columns.keys).describedAs("%s 컬럼 집합", table).isEqualTo(expected.columns.keys)
            expected.columns.forEach { (column, expectedColumn) ->
                assertColumnMatches(table, column, actual.columns.getValue(column), expectedColumn, tolerateNativeEnum)
            }
            assertThat(actual.indexes).describedAs("%s 인덱스", table).isEqualTo(expected.indexes)
        }
    }

    private fun assertColumnMatches(
        table: String,
        column: String,
        actual: ColumnShape,
        expected: ColumnShape,
        tolerateNativeEnum: Boolean,
    ) {
        assertThat(actual.nullable).describedAs("%s.%s NULL 허용", table, column).isEqualTo(expected.nullable)
        assertThat(actual.default).describedAs("%s.%s 기본값", table, column).isEqualTo(expected.default)
        assertThat(actual.extra).describedAs("%s.%s EXTRA", table, column).isEqualTo(expected.extra)
        if (actual.type == expected.type) return

        // Hibernate는 enum을 네이티브 enum으로, 마이그레이션은 VARCHAR로 만든다. 길이가 모든 값을 담는지까지 본다.
        val pair = setOf(actual.type.substringBefore("("), expected.type.substringBefore("("))
        assertThat(tolerateNativeEnum && pair == setOf("enum", "varchar"))
            .describedAs("%s.%s 타입 (%s vs %s)", table, column, actual.type, expected.type)
            .isTrue()
        val (enumType, varcharType) = if (actual.type.startsWith("enum")) actual.type to expected.type else expected.type to actual.type
        assertThat(varcharLengthOf(varcharType))
            .describedAs("%s.%s VARCHAR 길이가 enum 값(%s)을 담아야 한다", table, column, enumType)
            .isGreaterThanOrEqualTo(longestEnumValueOf(enumType))
    }

    private fun varcharLengthOf(type: String): Int = type.substringAfter("(").substringBefore(")").toInt()

    private fun longestEnumValueOf(type: String): Int =
        type
            .substringAfter("(")
            .substringBeforeLast(")")
            .split(",")
            .maxOf { it.trim().trim('\'').length }

    /**
     * Hibernate가 생성한 스크립트에서 주어진 테이블을 건드리는 문장만 원래 순서로 고른다.
     * FK는 뺀다 — 이 PR이 엔티티 선언에서 떼어냈고, 마이그레이션도 만들지 않는다.
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

    /**
     * ddl-auto가 V29 직전까지 만들어 두었던 모양 — 이 PR이 추가하는 컬럼·인덱스가 아직 없고
     * status는 ON_HOLD 없는 네이티브 enum이다.
     */
    private fun preV29DriftStatements(): List<String> =
        entitySchemaStatements(DDL_AUTO_TABLES)
            .filterNot { statement -> ADDED_INDEXES.any { statement.contains(it) } } +
            ADDED_SETTLEMENT_COLUMNS.map { "ALTER TABLE settlements DROP COLUMN $it" } +
            listOf(
                "ALTER TABLE settlements MODIFY COLUMN status enum('PENDING','PAID','CANCELLED') NOT NULL",
                "ALTER TABLE payouts MODIFY COLUMN status enum('PENDING','SENT','PAID','FAILED') NOT NULL",
                "ALTER TABLE settlement_execution_batches MODIFY COLUMN status enum('GENERATED','PROCESSING','COMPLETED') NOT NULL",
            )

    /** ddl-auto가 걸어 둔 FK를 흉내낸다 — 제약명은 Hibernate가 해시로 짓기 때문에 이름으로 찾아 떼는지 본다. */
    private fun driftForeignKeyStatements(): List<String> =
        listOf(
            "ALTER TABLE settlements ADD CONSTRAINT FKdriftsettlementspayout FOREIGN KEY (payout_id) REFERENCES payouts (id)",
            "ALTER TABLE payouts ADD CONSTRAINT FKdriftpayoutsbatch FOREIGN KEY (batch_id) REFERENCES settlement_execution_batches (id)",
        )

    /** V29 적용 전에 이미 있던 정산 1건 — 보강 컬럼 백필이 실제 주문을 가리키는지 보려면 데이터가 필요하다. */
    private fun legacySettlementRows(): List<String> =
        listOf(
            """
            INSERT INTO order_groups (id, public_id, buyer_id, receiver_name_enc, contact_number_enc, zip_code,
                                      base_address, coupon_discount_amount, used_point, total_product_amount,
                                      total_shipping_fee, final_payment_amount, status, idempotency_key,
                                      created_at, updated_at)
            VALUES (9101, 'LEGACYGROUP00000000000029', 5101, 'enc', 'enc', '12345', '서울시 애착구',
                    0, 0, 10000, 3000, 13000, 'PAID', 'legacy-key-29', NOW(6), NOW(6))
            """.trimIndent(),
            """
            INSERT INTO orders (id, public_id, order_group_id, status, seller_id, allocated_coupon_discount,
                                seller_shipping_fee, extension_count, created_at, updated_at)
            VALUES ($SEEDED_ORDER_ID, 'LEGACYORDER00000000000029', 9101, 'PURCHASE_CONFIRMED', $SEEDED_SELLER_ID,
                    0, 3000, 0, NOW(6), NOW(6))
            """.trimIndent(),
            """
            INSERT INTO order_items (id, order_id, product_id, option_combination_id, option_name_snapshot, quantity,
                                     unit_price_snapshot, discount_allocated_amount, item_status, product_version_id,
                                     created_at, updated_at)
            VALUES ($SEEDED_ORDER_ITEM_ID, $SEEDED_ORDER_ID, 7101, 7102, '기본 / 1개', 1, 10000, 0, 'ORDERED', 7103,
                    NOW(6), NOW(6))
            """.trimIndent(),
            """
            INSERT INTO settlements (id, seller_id, order_item_id, gross_amount, commission, pg_fee,
                                     seller_coupon_burden, net_amount, status, idempotency_key, created_at, updated_at)
            VALUES ($SEEDED_SETTLEMENT_ID, $SEEDED_SELLER_ID, $SEEDED_ORDER_ITEM_ID, 10000, 800, 280, 0, 8920, 'PENDING',
                    'SETTLE:ORDER_ITEM:$SEEDED_ORDER_ITEM_ID', NOW(6), NOW(6))
            """.trimIndent(),
        )

    /** 주문품목이 사라진 정산 행 — order_id 백필이 채울 수 없는 모양이다. */
    private fun orphanSettlementRow(): List<String> =
        listOf(
            """
            INSERT INTO settlements (id, seller_id, order_item_id, gross_amount, commission, pg_fee,
                                     seller_coupon_burden, net_amount, status, idempotency_key, created_at, updated_at)
            VALUES (9105, $SEEDED_SELLER_ID, 8888, 5000, 400, 140, 0, 4460, 'PENDING',
                    'SETTLE:ORDER_ITEM:8888', NOW(6), NOW(6))
            """.trimIndent(),
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
            SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT, EXTRA
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = '$table'
            """.trimIndent(),
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

    private fun foreignKeysOf(
        url: String,
        table: String,
    ): List<String> =
        query(
            url,
            """
            SELECT CONSTRAINT_NAME FROM information_schema.TABLE_CONSTRAINTS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = '$table' AND CONSTRAINT_TYPE = 'FOREIGN KEY'
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

    private data class TableShape(
        val columns: Map<String, ColumnShape>,
        val indexes: Map<String, String>,
    )

    companion object {
        private const val MIGRATION_NAME = "V29__create_settlement_tables_and_fee_policy.sql"
        private const val ENTITY_SCHEMA_SCRIPT = "build/v29-entity-schema.sql"

        // MySQLContainer의 기본 root 계정. 테스트 전용 계정은 DB를 만들 권한이 없다.
        private const val ROOT_USER = "root"
        private const val ROOT_PASSWORD = "test"

        private const val SEEDED_ORDER_ID = 9102L
        private const val SEEDED_ORDER_ITEM_ID = 9103L
        private const val SEEDED_SELLER_ID = 6101L
        private const val SEEDED_SETTLEMENT_ID = 9104L

        private val CREATE_TABLE = Regex("^create table (?:if not exists )?`?([a-z_0-9]+)`?", RegexOption.IGNORE_CASE)
        private val CREATE_INDEX = Regex("^create index \\S+ on `?([a-z_0-9]+)`?", RegexOption.IGNORE_CASE)
        private val ALTER_TABLE = Regex("^alter table (?:if exists )?`?([a-z_0-9]+)`?", RegexOption.IGNORE_CASE)
        private val FOREIGN_KEY = Regex("foreign key", RegexOption.IGNORE_CASE)

        /** 이 PR이 settlements에 새로 더하는 컬럼 — ddl-auto 재현에서 떼야 한다. */
        private val ADDED_SETTLEMENT_COLUMNS =
            listOf(
                "order_id",
                "shipping_fee",
                "refund_deduction",
                "platform_fee_rate",
                "pg_fee_rate",
                "settlement_month",
                "expected_payout_date",
            )

        /** 이 PR이 새로 붙이는 인덱스·UNIQUE — ddl-auto 재현에서 빼야 한다. */
        private val ADDED_INDEXES =
            listOf(
                "idx_settlements_seller_id_month",
                "idx_settlements_order_id",
                "idx_settlements_status",
                "idx_settlements_payout_id",
                "idx_payouts_seller_id_status",
                "idx_payouts_batch_id",
                "uk_settlement_execution_batches_base_date",
            )

        private val TARGET_TABLES =
            listOf(
                "settlements",
                "payouts",
                "settlement_execution_batches",
                "settlement_fee_policies",
            )

        /** settlement_fee_policies는 이 PR이 처음 만든다 — ddl-auto만으로 존재하던 테이블은 나머지 3개다. */
        private val DDL_AUTO_TABLES = TARGET_TABLES - "settlement_fee_policies"

        @JvmStatic
        @DynamicPropertySource
        fun containers(registry: DynamicPropertyRegistry) = IntegrationTestConfig.registerContainers(registry)
    }
}
