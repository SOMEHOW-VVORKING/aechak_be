package com.aechak.api.schema

import com.aechak.api.support.MigrationIntegrationTestBase
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.flywaydb.core.api.FlywayException
import org.junit.jupiter.api.Test

/**
 * [통합] V20이 주문 품목에 옵션명 스냅샷을 채우는 경로를 본다.
 *
 * V20은 네 단계를 조건부로 잇는다. `order_items`에 컬럼을 더하고, 연결된 옵션 조합의 이름으로
 * 백필하고, 그 뒤 NOT NULL로 조인다. 조건이 전부 `order_items`·`option_combinations` 존재
 * 여부인데 둘 다 그 시점에는 없다 — `order_items`는 V21이 만들고 `option_combinations`는
 * Flyway가 아예 만들지 않는다(ddl-auto 소유). 그래서 **CI에서는 네 단계가 모두 건너뛰어진다.**
 *
 * 깨지면 기존 주문의 옵션명이 비거나, 백필이 빠진 채 NOT NULL이 걸려 배포가 멈춘다.
 */
class V20OptionNameSnapshotMigrationIntegrationTest : MigrationIntegrationTestBase() {
    @Test
    fun `두 테이블이 없는 빈 DB에서는 네 단계를 건너뛰고 성공으로 기록된다`() {
        val url = recreateDatabase("v20_absent")

        migrateTo(url, "20")

        assertThat(appliedSuccessfully(url, "20"))
            .describedAs("V20이 성공으로 기록돼야 그 뒤 버전이 적용된다. CI가 지나는 경로다")
            .containsExactly("1")
        assertThat(scalar(url, "SELECT COUNT(*) FROM information_schema.TABLES $IN_SCHEMA AND TABLE_NAME = 'order_items'"))
            .describedAs("V20이 order_items를 만들지는 않는다")
            .isEqualTo("0")
    }

    @Test
    fun `주문 품목의 옵션명이 조합 이름으로 채워지고 NOT NULL로 조여진다`() {
        val url = recreateDatabase("v20_backfill")
        migrateBefore(url, "20")
        execute(url, devShapedTables() + seedCombinations() + seedOrderItems(combinationIds = listOf(1L, 2L, 1L)))

        migrateTo(url, "20")

        assertThat(query(url, "SELECT CONCAT(id, ':', option_name_snapshot) FROM order_items ORDER BY id"))
            .describedAs("주문 시점 옵션명이 조합의 현재 이름으로 들어가야 한다")
            .containsExactly("1:$HARNESS_M", "2:$BRUSH", "3:$HARNESS_M")
        assertThat(snapshotNullable(url))
            .describedAs("백필이 끝나면 NOT NULL로 조여져 이후 주문이 옵션명 없이 들어오지 못한다")
            .isEqualTo("NO")
        assertThat(
            query(
                url,
                "SELECT COLUMN_NAME FROM information_schema.COLUMNS $IN_SCHEMA AND TABLE_NAME = 'reviews' AND COLUMN_NAME = 'option_combination_id'",
            ),
        ).describedAs("리뷰는 order_item으로만 연결하므로 조합 참조 컬럼이 지워져야 한다")
            .isEmpty()
    }

    @Test
    fun `조합이 사라진 주문 품목이 있으면 빈 스냅샷을 만들지 않고 실패한다`() {
        val url = recreateDatabase("v20_orphan")
        migrateBefore(url, "20")
        // 조합 99는 심지 않는다. 백필 JOIN이 그 행을 건너뛰어 NULL로 남는다.
        execute(url, devShapedTables() + seedCombinations() + seedOrderItems(combinationIds = listOf(1L, 99L)))

        assertThatThrownBy { migrateTo(url, "20") }
            .describedAs("고아 행을 빈 문자열로 덮지 않고 그 자리에서 멈춰야 한다")
            .isInstanceOf(FlywayException::class.java)

        assertThat(snapshotNullable(url))
            .describedAs("NOT NULL 전환이 적용되지 않아야 한다")
            .isEqualTo("YES")
        assertThat(query(url, "SELECT CONCAT(id, ':', COALESCE(option_name_snapshot, '<NULL>')) FROM order_items ORDER BY id"))
            .describedAs("조합이 있는 행은 채워지고 없는 행은 빈 문자열이 아니라 NULL로 남아야 한다")
            .containsExactly("1:$HARNESS_M", "2:<NULL>")
        assertThat(appliedSuccessfully(url, "20"))
            .describedAs("실패로 기록돼 다음 배포가 같은 자리에서 다시 멈춰야 한다")
            .containsExactly("0")
    }

    private fun snapshotNullable(url: String): String? =
        scalar(
            url,
            "SELECT IS_NULLABLE FROM information_schema.COLUMNS $IN_SCHEMA " +
                "AND TABLE_NAME = 'order_items' AND COLUMN_NAME = 'option_name_snapshot'",
        )

    /**
     * V20 시점의 두 테이블을 dev 모양으로 만든다.
     *
     * `order_items`는 V21의 선언에서 `option_name_snapshot`만 뺀 것이다 — V20이 더하는 컬럼이다.
     * `AFTER option_combination_id`로 더하므로 그 컬럼이 반드시 있어야 한다.
     */
    private fun devShapedTables() =
        listOf(
            "CREATE TABLE option_combinations (id BIGINT PRIMARY KEY, name VARCHAR(255) NOT NULL)",
            """
            CREATE TABLE order_items
            (
                id                        BIGINT      NOT NULL AUTO_INCREMENT,
                order_id                  BIGINT      NOT NULL,
                product_id                BIGINT      NOT NULL,
                option_combination_id     BIGINT      NOT NULL,
                quantity                  INT         NOT NULL,
                unit_price_snapshot       BIGINT      NOT NULL,
                discount_allocated_amount BIGINT      NOT NULL,
                item_status               VARCHAR(30) NOT NULL,
                product_version_id        BIGINT      NOT NULL,
                created_at                DATETIME(6) NOT NULL,
                updated_at                DATETIME(6) NOT NULL,
                PRIMARY KEY (id)
            ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
            """.trimIndent(),
        )

    private fun seedCombinations() =
        listOf(
            "INSERT INTO option_combinations (id, name) VALUES (1, '$HARNESS_M'), (2, '$BRUSH')",
        )

    private fun seedOrderItems(combinationIds: List<Long>) =
        combinationIds.mapIndexed { index, combinationId ->
            "INSERT INTO order_items (id, order_id, product_id, option_combination_id, quantity, unit_price_snapshot, " +
                "discount_allocated_amount, item_status, product_version_id, created_at, updated_at) " +
                "VALUES (${index + 1}, 1, 1, $combinationId, 1, 10000, 0, 'ORDERED', 1, NOW(6), NOW(6))"
        }

    companion object {
        // 옵션명에 한글과 슬래시가 들어간다. utf8mb4로 오가지 않으면 이 단언이 먼저 깨진다.
        private const val HARNESS_M = "리트리버 하네스 / M / 검정"
        private const val BRUSH = "장모종용 브러시"
    }
}
