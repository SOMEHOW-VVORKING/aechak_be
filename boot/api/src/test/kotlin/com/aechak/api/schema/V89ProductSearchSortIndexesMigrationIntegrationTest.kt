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
 * V89(products.review_count 컬럼, product_stats에서 채우기, 인기순과 낮은 가격순 정렬 인덱스)의 가드 분기를 본다.
 * 깨지면 products가 없는 새 DB에서 마이그레이션이 실패하거나, 기존 DB의 인기순이 리뷰 수를 0으로 읽는다.
 * 컨텍스트를 새로 띄우지 않으려고 공용 베이스를 쓴다. V89는 별도 DB에 적용한다.
 */
class V89ProductSearchSortIndexesMigrationIntegrationTest : IntegrationTestBase() {
    @Autowired
    private lateinit var dataSource: DataSource

    @Test
    fun `products가 없는 DB에서는 아무것도 만들지 않고 성공으로 기록된다`() {
        val url = recreateDatabase("v89_no_products")

        flyway(url).migrate()

        assertThat(scalar(url, "SELECT COUNT(*) FROM information_schema.TABLES $IN_SCHEMA AND TABLE_NAME = 'products'")).isEqualTo("0")
        assertThat(scalar(url, "SELECT success FROM flyway_schema_history WHERE version = '89'")).isEqualTo("1")
    }

    @Test
    fun `변경 직전 DDL의 products에 컬럼과 인덱스를 만들고 product_stats의 리뷰 수로 채운다`() {
        val url = recreateDatabase("v89_products")
        migrateBeforeV89(url)
        execute(url, preChangeProductsDdl())
        execute(url, showCreate(sharedUrl(), "product_stats"))
        assertThat(reviewCountColumn(url)).describedAs("적용 전에는 컬럼이 없어야 ADD COLUMN 분기가 실제로 돈다").isEmpty()
        assertThat(sortIndexes(url)).describedAs("적용 전에는 인덱스가 없어야 CREATE INDEX 분기가 실제로 돈다").isEmpty()
        val reviewed = insertProduct(url, "리뷰 7")
        val likedOnly = insertProduct(url, "찜만")
        val noStats = insertProduct(url, "통계 없음")
        insertStats(url, reviewed, reviewCount = 7)
        insertStats(url, likedOnly, reviewCount = 0)

        flyway(url).migrate()

        assertThat(reviewCountColumn(url)).containsExactly("int:NO:0")
        assertThat(sortIndexes(url)).containsExactly(*SORT_INDEXES)
        assertThat(reviewCounts(url)).containsExactly("$reviewed:7", "$likedOnly:0", "$noStats:0")
    }

    @Test
    fun `product_stats가 없으면 채우기만 건너뛰고 컬럼과 인덱스는 만든다`() {
        val url = recreateDatabase("v89_no_stats")
        migrateBeforeV89(url)
        execute(url, preChangeProductsDdl())
        val product = insertProduct(url, "통계 테이블 없음")

        flyway(url).migrate()

        assertThat(reviewCountColumn(url)).containsExactly("int:NO:0")
        assertThat(sortIndexes(url)).containsExactly(*SORT_INDEXES)
        assertThat(reviewCounts(url)).containsExactly("$product:0")
    }

    @Test
    fun `ddl-auto 테스트 스키마의 컬럼과 엔티티 인덱스가 마이그레이션과 같다`() {
        assertThat(reviewCountColumn(sharedUrl())).containsExactly("int:NO:0")
        assertThat(sortIndexes(sharedUrl())).containsExactly(*SORT_INDEXES)
    }

    /** 공용 테스트 스키마의 products DDL에서 V89가 더하는 컬럼과 인덱스를 뺀 것 */
    private fun preChangeProductsDdl(): String {
        val current = showCreate(sharedUrl(), "products")
        val ddl =
            current
                .replace(Regex("\\n\\s*`review_count`[^\\n]*,(?=\\n)"), "")
                .replace(
                    Regex(",\\n\\s*KEY `(ix_products_popular|ix_products_discount_price|ix_products_regular_price)`[^\\n]*?(?=,?\\n)"),
                    "",
                )
        assertThat(current)
            .describedAs("공용 테스트 스키마에는 컬럼과 인덱스가 있어야 한다")
            .contains("`review_count`", "ix_products_popular", "ix_products_discount_price", "ix_products_regular_price")
        assertThat(ddl).doesNotContain("review_count", "ix_products_popular", "ix_products_discount_price", "ix_products_regular_price")
        return ddl
    }

    private fun insertProduct(
        url: String,
        name: String,
    ): Long =
        connect(url).use { conn ->
            conn
                .prepareStatement(
                    "INSERT INTO products (public_id, category_id, seller_id, name, regular_price, sale_status, inspection_status, " +
                        "version, created_at, updated_at) " +
                        "SELECT ?, MIN(id), 1, ?, 10000, 'ON_SALE', 'APPROVED', 0, NOW(6), NOW(6) FROM categories",
                    arrayOf("id"),
                ).use { statement ->
                    statement.setString(1, name.hashCode().toString().padStart(26, '0'))
                    statement.setString(2, name)
                    statement.executeUpdate()
                    statement.generatedKeys.use { rs ->
                        rs.next()
                        rs.getLong(1)
                    }
                }
        }

    private fun insertStats(
        url: String,
        productId: Long,
        reviewCount: Int,
    ) = execute(
        url,
        "INSERT INTO product_stats (product_id, review_count, rating_sum, like_count, created_at, updated_at) " +
            "VALUES ($productId, $reviewCount, ${reviewCount * 4}, 1, NOW(6), NOW(6))",
    )

    private fun reviewCountColumn(url: String): List<String> =
        query(
            url,
            "SELECT CONCAT(DATA_TYPE, ':', IS_NULLABLE, ':', COLUMN_DEFAULT) FROM information_schema.COLUMNS " +
                "$IN_SCHEMA AND TABLE_NAME = 'products' AND COLUMN_NAME = 'review_count'",
        )

    private fun sortIndexes(url: String): List<String> =
        query(
            url,
            "SELECT CONCAT(INDEX_NAME, ':', SEQ_IN_INDEX, ':', COLUMN_NAME, ':', COLLATION) FROM information_schema.STATISTICS " +
                "$IN_SCHEMA AND TABLE_NAME = 'products' " +
                "AND INDEX_NAME IN ('ix_products_popular', 'ix_products_discount_price', 'ix_products_regular_price') " +
                "ORDER BY INDEX_NAME, SEQ_IN_INDEX",
        )

    private fun reviewCounts(url: String): List<String> = query(url, "SELECT CONCAT(id, ':', review_count) FROM products ORDER BY id")

    private fun showCreate(
        url: String,
        table: String,
    ): String =
        connect(url).use { conn ->
            conn.createStatement().use { statement ->
                statement.executeQuery("SHOW CREATE TABLE $table").use { rs ->
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

    private fun sharedUrl(): String = adminUrl(dataSource.connection.use { it.catalog })

    private fun adminUrl(database: String?): String {
        val base = dataSource.connection.use { it.metaData.url }.substringBeforeLast('/')
        return "$base/${database ?: ""}"
    }

    private fun connect(url: String): Connection = DriverManager.getConnection(url, ROOT_USER, ROOT_PASSWORD)

    private fun flyway(url: String): Flyway = Flyway.configure().dataSource(url, ROOT_USER, ROOT_PASSWORD).load()

    /** V89 바로 앞 버전까지 적용한다 */
    private fun migrateBeforeV89(url: String) {
        val previous =
            flyway(url)
                .info()
                .all()
                .map { it.version }
                .filter { it < V89 }
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
        private val V89: MigrationVersion = MigrationVersion.fromVersion("89")
        private val SORT_INDEXES =
            arrayOf(
                "ix_products_discount_price:1:discount_price:A",
                "ix_products_discount_price:2:id:D",
                "ix_products_popular:1:review_count:D",
                "ix_products_popular:2:id:D",
                "ix_products_regular_price:1:regular_price:A",
                "ix_products_regular_price:2:id:D",
            )
    }
}
