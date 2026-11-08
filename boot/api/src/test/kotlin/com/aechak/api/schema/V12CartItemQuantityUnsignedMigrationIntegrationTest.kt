package com.aechak.api.schema

import com.aechak.api.support.MigrationIntegrationTestBase
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.flywaydb.core.api.FlywayException
import org.junit.jupiter.api.Test
import java.sql.SQLException

/**
 * [통합] V12가 장바구니 수량을 unsigned로 조이는 경로를 본다.
 *
 * `cart_items` 는 Flyway 가 아예 만들지 않는다(ddl-auto 소유). 그래서 **CI 에서는 MODIFY 가
 * 건너뛰어진다.**
 *
 * 깨지면 음수 수량 방어가 DB 에 생기지 않는다. ddl-auto 는 컬럼을 추가만 하고 타입을 바꾸지 않아
 * 이미 `INT` 인 기존 테이블에는 엔티티의 unsigned 선언이 붙지 않는다.
 *
 * 주석은 "앱에서 1 이상 99 이하만 통과하므로 범위 밖 기존 행은 없음"으로 단정한다. 그 단정이
 * 깨진 DB 에서 어떻게 되는지를 아래에서 본다 — 조용히 잘리는 것과 멈추는 것은 결과가 다르다.
 */
class V12CartItemQuantityUnsignedMigrationIntegrationTest : MigrationIntegrationTestBase() {
    @Test
    fun `cart_items가 없는 빈 DB에서는 MODIFY를 건너뛰고 성공으로 기록된다`() {
        val url = recreateDatabase("v12_absent")

        migrateTo(url, "12")

        assertThat(appliedSuccessfully(url, "12"))
            .describedAs("V12가 성공으로 기록돼야 그 뒤 버전이 적용된다. CI가 지나는 경로다")
            .containsExactly("1")
    }

    @Test
    fun `INT이던 수량이 SMALLINT UNSIGNED로 바뀌고 기존 값이 보존된다`() {
        val url = seededDatabase("v12_apply", quantities = listOf(1, 50, 99))

        migrateTo(url, "12")

        assertThat(quantityColumn(url))
            .describedAs("엔티티 선언과 같은 타입이어야 음수 방어가 DB 에 생긴다")
            .isEqualTo("smallint:unsigned:NO")
        assertThat(query(url, "SELECT CONCAT(id, ':', quantity) FROM cart_items ORDER BY id"))
            .describedAs("범위 안 값은 그대로 남아야 한다")
            .containsExactly("1:1", "2:50", "3:99")
    }

    @Test
    fun `적용 뒤에는 음수 수량을 넣을 수 없다`() {
        val url = seededDatabase("v12_guard", quantities = listOf(1))
        migrateTo(url, "12")

        assertThatThrownBy { execute(url, "UPDATE cart_items SET quantity = -1 WHERE id = 1") }
            .describedAs("이 타입 변경의 목적이 음수 차단이다")
            .isInstanceOf(SQLException::class.java)
    }

    @Test
    fun `음수 수량이 남아 있으면 조용히 0으로 바꾸지 않고 실패한다`() {
        val url = seededDatabase("v12_negative", quantities = listOf(1, -3))

        assertThatThrownBy { migrateTo(url, "12") }
            .describedAs("0 으로 잘리면 장바구니에 수량 0 행이 생겨 이후 계산이 틀린다. 멈추는 쪽이 맞다")
            .isInstanceOf(FlywayException::class.java)

        assertThat(query(url, "SELECT CONCAT(id, ':', quantity) FROM cart_items ORDER BY id"))
            .describedAs("실패했으면 값이 그대로여야 한다")
            .containsExactly("1:1", "2:-3")
        assertThat(appliedSuccessfully(url, "12")).containsExactly("0")
    }

    @Test
    fun `상한을 넘는 수량이 남아 있으면 잘라 내지 않고 실패한다`() {
        val url = seededDatabase("v12_overflow", quantities = listOf(70000))

        assertThatThrownBy { migrateTo(url, "12") }
            .describedAs("SMALLINT UNSIGNED 상한은 65535 다. 잘리면 주문 수량이 바뀐다")
            .isInstanceOf(FlywayException::class.java)

        assertThat(scalar(url, "SELECT quantity FROM cart_items WHERE id = 1")).isEqualTo("70000")
    }

    /** `타입:부호:NULL허용` 꼴. */
    private fun quantityColumn(url: String): String? =
        scalar(
            url,
            "SELECT CONCAT_WS(':', DATA_TYPE, IF(COLUMN_TYPE LIKE '%unsigned%', 'unsigned', 'signed'), IS_NULLABLE) " +
                "FROM information_schema.COLUMNS $IN_SCHEMA AND TABLE_NAME = 'cart_items' AND COLUMN_NAME = 'quantity'",
        )

    private fun seededDatabase(
        name: String,
        quantities: List<Int>,
    ): String {
        val url = recreateDatabase(name)
        migrateBefore(url, "12")
        execute(
            url,
            "CREATE TABLE cart_items (" +
                "id BIGINT NOT NULL AUTO_INCREMENT, cart_id BIGINT NOT NULL, quantity INT NOT NULL, PRIMARY KEY (id)" +
                ") ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci",
            "INSERT INTO cart_items (id, cart_id, quantity) VALUES " +
                quantities.mapIndexed { index, quantity -> "(${index + 1}, 1, $quantity)" }.joinToString(", "),
        )
        return url
    }
}
