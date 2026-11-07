package com.aechak.api.schema

import com.aechak.api.support.MigrationIntegrationTestBase
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.flywaydb.core.api.FlywayException
import org.junit.jupiter.api.Test
import java.sql.SQLException

/**
 * [통합] V18이 옵션 조합 재고를 unsigned로 조이는 경로를 본다.
 *
 * `option_combinations` 는 Flyway 가 아예 만들지 않는다(ddl-auto 소유). 그래서 **CI 에서는 MODIFY 가
 * 건너뛰어진다.**
 *
 * 깨지면 음수 재고 방어가 DB 에 생기지 않는다. 재고는 주문과 취소가 함께 쓰는 값이라 음수로 내려가면
 * 재고 복원 계산이 어긋난다.
 *
 * 주석은 "등록 검증과 changeStock 의 0 하한이 0 이상만 만들므로 범위 밖 기존 행은 없음"으로 단정한다.
 */
class V18OptionCombinationStockUnsignedMigrationIntegrationTest : MigrationIntegrationTestBase() {
    @Test
    fun `option_combinations가 없는 빈 DB에서는 MODIFY를 건너뛰고 성공으로 기록된다`() {
        val url = recreateDatabase("v18_absent")

        migrateTo(url, "18")

        assertThat(appliedSuccessfully(url, "18"))
            .describedAs("V18이 성공으로 기록돼야 그 뒤 버전이 적용된다. CI가 지나는 경로다")
            .containsExactly("1")
    }

    @Test
    fun `INT이던 재고가 INT UNSIGNED로 바뀌고 기존 값이 보존된다`() {
        val url = seededDatabase("v18_apply", stocks = listOf(0, 100, 999999))

        migrateTo(url, "18")

        assertThat(stockColumn(url))
            .describedAs("엔티티 선언과 같은 타입이어야 음수 방어가 DB 에 생긴다")
            .isEqualTo("int:unsigned:NO")
        assertThat(query(url, "SELECT CONCAT(id, ':', stock_quantity) FROM option_combinations ORDER BY id"))
            .describedAs("0 은 품절을 뜻하는 정상값이라 보존돼야 한다")
            .containsExactly("1:0", "2:100", "3:999999")
    }

    @Test
    fun `적용 뒤에는 음수 재고를 넣을 수 없다`() {
        val url = seededDatabase("v18_guard", stocks = listOf(5))
        migrateTo(url, "18")

        assertThatThrownBy { execute(url, "UPDATE option_combinations SET stock_quantity = -1 WHERE id = 1") }
            .describedAs("동시 주문이 0 하한 검사를 비껴가도 이 타입이 막는다")
            .isInstanceOf(SQLException::class.java)
    }

    @Test
    fun `음수 재고가 남아 있으면 조용히 0으로 바꾸지 않고 실패한다`() {
        val url = seededDatabase("v18_negative", stocks = listOf(5, -2))

        assertThatThrownBy { migrateTo(url, "18") }
            .describedAs("0 으로 잘리면 품절로 보여 팔 수 있는 재고가 사라진다. 멈추는 쪽이 맞다")
            .isInstanceOf(FlywayException::class.java)

        assertThat(query(url, "SELECT CONCAT(id, ':', stock_quantity) FROM option_combinations ORDER BY id"))
            .containsExactly("1:5", "2:-2")
        assertThat(appliedSuccessfully(url, "18")).containsExactly("0")
    }

    private fun stockColumn(url: String): String? =
        scalar(
            url,
            "SELECT CONCAT_WS(':', DATA_TYPE, IF(COLUMN_TYPE LIKE '%unsigned%', 'unsigned', 'signed'), IS_NULLABLE) " +
                "FROM information_schema.COLUMNS $IN_SCHEMA AND TABLE_NAME = 'option_combinations' " +
                "AND COLUMN_NAME = 'stock_quantity'",
        )

    private fun seededDatabase(
        name: String,
        stocks: List<Int>,
    ): String {
        val url = recreateDatabase(name)
        migrateBefore(url, "18")
        execute(
            url,
            "CREATE TABLE option_combinations (" +
                "id BIGINT NOT NULL AUTO_INCREMENT, product_id BIGINT NOT NULL, " +
                "stock_quantity INT NOT NULL, PRIMARY KEY (id)" +
                ") ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci",
            "INSERT INTO option_combinations (id, product_id, stock_quantity) VALUES " +
                stocks.mapIndexed { index, stock -> "(${index + 1}, 1, $stock)" }.joinToString(", "),
        )
        return url
    }
}
