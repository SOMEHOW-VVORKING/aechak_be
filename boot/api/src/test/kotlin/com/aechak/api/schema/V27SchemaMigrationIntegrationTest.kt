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
 * V27(ddl-auto 의존 테이블 Flyway 이관)의 안전성 검증. 깨지면 "dev에 적용하면 스키마가 갈린다"는 뜻이다.
 *
 * 네 개의 스키마를 컨테이너에 따로 만들어 대조한다.
 *  - fresh  : 빈 DB에 Flyway 전체 적용 — 기준
 *  - entity : Hibernate가 엔티티 선언대로 만들었을 DDL(스키마 생성 스크립트로 받아 적용)
 *  - legacy : dev 재현 — ddl-auto가 V10 전에 테이블을 만들고 FK까지 걸어 둔 DB
 *  - late   : V10 이후에 ddl-auto가 테이블을 만든 DB(그 뒤 새로 만든 환경이 이 모양이다)
 *
 * 대조 대상 목록에는 이관 대상만이 아니라 이미 Flyway가 소유한 테이블(orders, payments, product_drafts, sns_user_stats, sns_blocks, sns_mutes, sns_follows)도 둔다 —
 * 그 테이블에 컬럼을 더할 때 엔티티 선언과 어긋나는 것을 같은 자리에서 잡는다.
 *
 * `ddl-auto=validate` 부팅으로는 이 정합을 볼 수 없다. users, products 등 엔티티 테이블 상당수가
 * 아직 ddl-auto 전용이라, 빈 DB에서 validate가 그 테이블들에서
 * 먼저 깨진다. 그래서 이관 대상 테이블만 떼어 직접 대조한다.
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
    private lateinit var lateUrl: String

    @BeforeAll
    fun prepareSchemas() {
        freshUrl = recreateDatabase("v27_fresh")
        flyway(freshUrl).migrate()

        entityUrl = recreateDatabase("v27_entity")
        execute(entityUrl, entitySchemaStatements(TARGET_TABLES))

        // dev 재현: ddl-auto가 V10보다 먼저 테이블을 만들었고 FK도 걸려 있다. 기존 클레임 데이터도 심는다.
        legacyUrl = buildDriftSchema("v27_legacy", createdBeforeV10 = true, rows = legacyClaimRows())

        // V10 이후에 ddl-auto가 만든 DB: V10의 조건부 ALTER가 전부 건너뛰어져 제약이 비어 있다.
        lateUrl = buildDriftSchema("v27_late", createdBeforeV10 = false, rows = emptyList())
    }

    /**
     * ddl-auto가 만든 DB를 재현한 뒤 V27까지 적용한다.
     * orders는 V21이 만들지만 ddl-auto가 그 위에 enum 타입과 FK 보조 인덱스를 남기므로 그 모양까지 되돌린다.
     */
    private fun buildDriftSchema(
        name: String,
        createdBeforeV10: Boolean,
        rows: List<String>,
        migrateToLatest: Boolean = true,
    ): String {
        val url = recreateDatabase(name)
        if (createdBeforeV10) {
            migrateUpTo(url, "1")
            execute(url, preV27DriftStatements())
            migrateUpTo(url, "26")
        } else {
            migrateUpTo(url, "26")
            execute(url, preV27DriftStatements())
        }
        execute(url, ordersDriftStatements() + rows + driftForeignKeyStatements())
        if (migrateToLatest) flyway(url).migrate()
        return url
    }

    @Test
    fun `빈 DB에 Flyway 전체 적용이 성공하고 택배사가 시드된다`() {
        val applied = query(freshUrl, "SELECT version FROM flyway_schema_history WHERE success = 1") { it.getString(1) }
        assertThat(applied).contains("27")

        val couriers = query(freshUrl, "SELECT courier_code FROM courier_masters ORDER BY courier_code") { it.getString(1) }
        assertThat(couriers).containsExactly("CJ", "HANJIN", "LOGEN", "LOTTE", "POST")

        val inactive = query(freshUrl, "SELECT courier_code FROM courier_masters WHERE is_active = b'0'") { it.getString(1) }
        assertThat(inactive).isEmpty()
    }

    @Test
    fun `V27을 다시 실행해도 스키마가 그대로다`() {
        val before = TARGET_TABLES.associateWith { snapshot(freshUrl, it) }

        execute(freshUrl, statementsOf(migrationSql()))

        assertThat(TARGET_TABLES.associateWith { snapshot(freshUrl, it) }).isEqualTo(before)
    }

    @Test
    fun `V27을 다시 실행해도 이미 채운 클레임 데이터를 건드리지 않는다`() {
        val before = claimSnapshotOf(legacyUrl)

        execute(legacyUrl, statementsOf(migrationSql()))

        assertThat(claimSnapshotOf(legacyUrl)).isEqualTo(before)
    }

    @Test
    fun `주문으로 이어지지 않는 품목이 섞인 클레임이 있으면 적용이 실패한다`() {
        val orphanUrl =
            buildDriftSchema(
                "v27_orphan",
                createdBeforeV10 = true,
                rows = legacyClaimRows() + orphanClaimItemRow(),
                migrateToLatest = false,
            )

        // 백필이 채우지 못한 행이 남아 NOT NULL 좁히기가 막힌다 — 조용히 0을 넣지 않는다는 뜻이다.
        assertThatThrownBy { flyway(orphanUrl).migrate() }
            .hasMessageContaining("V27__migrate_ddl_auto_tables.sql")
            .hasMessageContaining("Invalid use of NULL value")
    }

    @Test
    fun `시드 재실행은 운영에서 고친 택배사 행을 되돌리지 않는다`() {
        // 운영 데이터를 바꿔 보는 테스트라 기준 스키마(fresh)를 더럽히지 않게 전용 DB를 쓴다.
        val seedUrl = recreateDatabase("v27_seed")
        flyway(seedUrl).migrate()
        execute(
            seedUrl,
            listOf("UPDATE courier_masters SET courier_name = '씨제이', is_active = b'0' WHERE courier_code = 'CJ'"),
        )

        execute(seedUrl, statementsOf(migrationSql()))

        val cj =
            query(seedUrl, "SELECT courier_name, is_active FROM courier_masters WHERE courier_code = 'CJ'") {
                it.getString(1) to it.getBoolean(2)
            }
        assertThat(cj).containsExactly("씨제이" to false)
        assertThat(query(seedUrl, "SELECT courier_code FROM courier_masters") { it.getString(1) }).hasSize(5)
    }

    @Test
    fun `Flyway가 만든 스키마가 엔티티 선언과 일치한다`() {
        assertSameShape(actualUrl = freshUrl, expectedUrl = entityUrl, tolerateNativeEnum = true)
    }

    @Test
    fun `ddl-auto가 V10 전에 만든 DB에 적용해도 빈 DB와 같은 모양이 된다`() {
        assertSameShape(actualUrl = legacyUrl, expectedUrl = freshUrl, tolerateNativeEnum = false)
    }

    @Test
    fun `ddl-auto가 V10 뒤에 만든 DB에 적용해도 빈 DB와 같은 모양이 된다`() {
        assertSameShape(actualUrl = lateUrl, expectedUrl = freshUrl, tolerateNativeEnum = false)
    }

    @Test
    fun `유저당 신청 1행 제약은 엔티티보다 DB가 더 좁다`() {
        // V10이 세운 결정(행 재사용 모델의 방어선). 엔티티 선언은 nullable이라 모양 비교에서 예외로 둔다.
        listOf(freshUrl, legacyUrl, lateUrl).forEach { url ->
            assertThat(snapshot(url, "seller_applications").columns.getValue("user_id").nullable).isFalse()
        }
    }

    @Test
    fun `기존 클레임의 비정규화 컬럼은 주문 경로로 채워진다`() {
        val filled =
            query(legacyUrl, "SELECT seller_id, order_id FROM claims ORDER BY id") {
                it.getLong(1) to it.getLong(2)
            }
        assertThat(filled).containsExactly(SEEDED_SELLER_ID to SEEDED_ORDER_ID)
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
        if ("$table.$column" !in DB_STRICTER_THAN_ENTITY) {
            assertThat(actual.nullable).describedAs("%s.%s NULL 허용", table, column).isEqualTo(expected.nullable)
        }
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
    private fun preV27DriftStatements(): List<String> =
        entitySchemaStatements(DDL_AUTO_TABLES)
            .filterNot { statement -> ADDED_INDEXES.any { statement.contains(it) } } +
            listOf(
                "ALTER TABLE claims DROP COLUMN seller_id",
                "ALTER TABLE claims DROP COLUMN order_id",
                "ALTER TABLE claims DROP COLUMN seller_memo",
            )

    /** ddl-auto가 걸어 둔 FK를 흉내낸다 — FK와 그 보조 인덱스가 있어도 조건부 ALTER가 걸리지 않아야 한다. */
    private fun driftForeignKeyStatements(): List<String> =
        listOf(
            "ALTER TABLE shipments ADD CONSTRAINT FKdriftshipmentsorder FOREIGN KEY (order_id) REFERENCES orders (id)",
            "ALTER TABLE claim_items ADD CONSTRAINT FKdriftclaimitemsclaim FOREIGN KEY (claim_id) REFERENCES claims (id)",
            "ALTER TABLE order_status_histories ADD CONSTRAINT FKdriftoshorder FOREIGN KEY (order_id) REFERENCES orders (id)",
            "ALTER TABLE orders ADD CONSTRAINT FKdriftordersgroup FOREIGN KEY (order_group_id) REFERENCES order_groups (id)",
        )

    /**
     * orders를 ddl-auto가 손댄 모양으로 되돌린다 — enum 타입과, 명명 인덱스 없이 FK 보조 인덱스만 있는 상태.
     * enum 값 목록은 엔티티 스크립트에서 그대로 떠 와 테스트가 상태값 추가에 뒤처지지 않게 한다.
     */
    private fun ordersDriftStatements(): List<String> {
        val ordersCreate = entitySchemaStatements(listOf("orders")).first { CREATE_TABLE.containsMatchIn(it) }
        return listOf(
            "ALTER TABLE orders MODIFY COLUMN status ${enumDefinitionOf(ordersCreate, "status")} NOT NULL",
            "ALTER TABLE orders MODIFY COLUMN confirm_type ${enumDefinitionOf(ordersCreate, "confirm_type")} NULL",
            "ALTER TABLE orders DROP INDEX idx_orders_order_group_id",
        )
    }

    private fun enumDefinitionOf(
        createStatement: String,
        column: String,
    ): String {
        val match = Regex("[(,]\\s*$column (enum ?\\([^)]*\\))", RegexOption.IGNORE_CASE).find(createStatement)
        return requireNotNull(match) { "$column 의 enum 선언을 엔티티 스크립트에서 찾지 못했습니다" }.groupValues[1]
    }

    /** V27 적용 전에 이미 있던 클레임 1건 — 비정규화 컬럼 백필이 실제 주문을 가리키는지 보려면 데이터가 필요하다. */
    private fun legacyClaimRows(): List<String> =
        listOf(
            """
            INSERT INTO order_groups (id, public_id, buyer_id, receiver_name_enc, contact_number_enc, zip_code,
                                      base_address, coupon_discount_amount, used_point, total_product_amount,
                                      total_shipping_fee, final_payment_amount, status, idempotency_key,
                                      created_at, updated_at)
            VALUES (9001, 'LEGACYGROUP00000000000001', 5001, 'enc', 'enc', '12345', '서울시 애착구',
                    0, 0, 10000, 3000, 13000, 'PAID', 'legacy-key-1', NOW(6), NOW(6))
            """.trimIndent(),
            """
            INSERT INTO orders (id, public_id, order_group_id, status, seller_id, allocated_coupon_discount,
                                seller_shipping_fee, extension_count, created_at, updated_at)
            VALUES ($SEEDED_ORDER_ID, 'LEGACYORDER00000000000001', 9001, 'DELIVERED', $SEEDED_SELLER_ID,
                    0, 3000, 0, NOW(6), NOW(6))
            """.trimIndent(),
            """
            INSERT INTO order_items (id, order_id, product_id, option_combination_id, option_name_snapshot, quantity,
                                     unit_price_snapshot, discount_allocated_amount, item_status, product_version_id,
                                     created_at, updated_at)
            VALUES (9003, $SEEDED_ORDER_ID, 7001, 7002, '기본 / 1개', 1, 10000, 0, 'ORDERED', 7003, NOW(6), NOW(6))
            """.trimIndent(),
            """
            INSERT INTO claims (id, public_id, claim_type, status, version, requested_at, created_at, updated_at)
            VALUES (9004, 'LEGACYCLAIM00000000000001', 'CANCEL', 'CLAIM_REQUESTED', 0, NOW(6), NOW(6), NOW(6))
            """.trimIndent(),
            """
            INSERT INTO claim_items (id, claim_id, order_item_id, quantity, refund_amount, created_at, updated_at)
            VALUES (9005, 9004, 9003, 1, 10000, NOW(6), NOW(6))
            """.trimIndent(),
        )

    /** 주문품목이 사라진 클레임 품목 — 정상 품목과 섞이면 백필이 "주문 하나"로 오판할 수 있는 모양이다. */
    private fun orphanClaimItemRow(): List<String> =
        listOf(
            """
            INSERT INTO claim_items (id, claim_id, order_item_id, quantity, refund_amount, created_at, updated_at)
            VALUES (9006, 9004, 8888, 1, 5000, NOW(6), NOW(6))
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

    /** 백필 결과와 updated_at까지 — 재실행이 데이터를 건드리지 않는지 보려면 시각도 봐야 한다. */
    private fun claimSnapshotOf(url: String): List<String> =
        query(url, "SELECT id, seller_id, order_id, updated_at FROM claims ORDER BY id") {
            "${it.getLong(1)}/${it.getLong(2)}/${it.getLong(3)}/${it.getTimestamp(4)}"
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
        private const val MIGRATION_NAME = "V27__migrate_ddl_auto_tables.sql"
        private const val ENTITY_SCHEMA_SCRIPT = "build/v27-entity-schema.sql"

        // MySQLContainer의 기본 root 계정. 테스트 전용 계정은 DB를 만들 권한이 없다.
        private const val ROOT_USER = "root"
        private const val ROOT_PASSWORD = "test"

        private const val SEEDED_ORDER_ID = 9002L
        private const val SEEDED_SELLER_ID = 6001L

        private val CREATE_TABLE = Regex("^create table (?:if not exists )?`?([a-z_0-9]+)`?", RegexOption.IGNORE_CASE)
        private val CREATE_INDEX = Regex("^create index \\S+ on `?([a-z_0-9]+)`?", RegexOption.IGNORE_CASE)
        private val ALTER_TABLE = Regex("^alter table (?:if exists )?`?([a-z_0-9]+)`?", RegexOption.IGNORE_CASE)
        private val FOREIGN_KEY = Regex("foreign key", RegexOption.IGNORE_CASE)

        /** 엔티티 선언보다 DB가 더 좁은 컬럼 — NULL 허용 비교만 예외로 둔다. */
        private val DB_STRICTER_THAN_ENTITY = setOf("seller_applications.user_id")

        /** 이 PR이 새로 붙이는 인덱스 — ddl-auto 재현에서 빼야 한다. */
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
                "payments",
                "product_drafts",
                "sns_user_stats",
                "sns_blocks",
                "sns_mutes",
                "sns_follows",
            )

        /**
         * orders는 V21이, payments는 V24가, product_drafts는 V31이, sns_user_stats는 V35가, sns_blocks는 V48이, sns_mutes는 V49가, sns_follows는 V50이 이미 만든다 —
         * ddl-auto만으로 존재하던 테이블은 나머지 10개다.
         */
        private val DDL_AUTO_TABLES =
            TARGET_TABLES - "orders" - "payments" - "product_drafts" - "sns_user_stats" - "sns_blocks" - "sns_mutes" - "sns_follows"

        @JvmStatic
        @DynamicPropertySource
        fun containers(registry: DynamicPropertyRegistry) = IntegrationTestConfig.registerContainers(registry)
    }
}
