package com.aechak.api.schema

import com.aechak.api.support.MigrationIntegrationTestBase
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.flywaydb.core.api.FlywayException
import org.junit.jupiter.api.Test
import java.sql.SQLException

/**
 * [통합] V16이 상품 옵션 쪽 UNIQUE 둘을 거는 경로를 본다.
 *
 * `option_groups`·`option_combination_values` 는 Flyway 가 아예 만들지 않는다(ddl-auto 소유).
 * 그래서 **CI 에서는 두 단계가 건너뛰어진다.**
 *
 * 둘 다 ERD 가 요구하는데 엔티티에 선언이 없어 ddl-auto 가 만들지 않고 있던 제약이다. 깨지면
 * 지금 요청 DTO 만 막고 있는 규칙이 다른 입구가 생기는 순간 뚫린다.
 */
class V16ProductOptionConstraintsMigrationIntegrationTest : MigrationIntegrationTestBase() {
    @Test
    fun `두 테이블이 없는 빈 DB에서는 두 단계를 건너뛰고 성공으로 기록된다`() {
        val url = recreateDatabase("v16_absent")

        migrateTo(url, "16")

        assertThat(appliedSuccessfully(url, "16"))
            .describedAs("V16이 성공으로 기록돼야 그 뒤 버전이 적용된다. CI가 지나는 경로다")
            .containsExactly("1")
    }

    @Test
    fun `상품 안에서 옵션 그룹명이 유일해지고 한 조합에 같은 옵션값이 두 번 붙지 못한다`() {
        val url = seededDatabase("v16_apply", duplicate = Duplicate.NONE)

        migrateTo(url, "16")

        assertThat(indexes(url, "option_groups"))
            .describedAs("한 상품 안에서 옵션 그룹명은 유일하다")
            .contains(
                "uk_option_group_product_name:1:product_id:0",
                "uk_option_group_product_name:2:name:0",
            )
        assertThat(indexes(url, "option_combination_values"))
            .describedAs("같은 옵션값이 두 번 붙으면 조합 서명과 연결 행이 어긋난다")
            .contains(
                "uk_option_combination_value_pair:1:option_combination_id:0",
                "uk_option_combination_value_pair:2:option_value_id:0",
            )
    }

    @Test
    fun `적용 뒤에는 같은 상품에 같은 이름의 옵션 그룹을 또 만들 수 없다`() {
        val url = seededDatabase("v16_group_guard", duplicate = Duplicate.NONE)
        migrateTo(url, "16")

        assertThatThrownBy {
            execute(url, "INSERT INTO option_groups (id, product_id, name) VALUES (99, 1, '크기')")
        }.describedAs("요청 DTO 밖의 입구가 생겨도 이 UNIQUE 가 막는다")
            .isInstanceOf(SQLException::class.java)
    }

    @Test
    fun `같은 이름의 옵션 그룹이 이미 둘 있으면 UNIQUE를 걸지 못하고 실패한다`() {
        val url = seededDatabase("v16_dup_group", duplicate = Duplicate.GROUP_NAME)

        assertThatThrownBy { migrateTo(url, "16") }
            .describedAs("주석이 적은 대로 중복을 지우고 다시 돌려야 한다")
            .isInstanceOf(FlywayException::class.java)

        assertThat(appliedSuccessfully(url, "16")).containsExactly("0")
    }

    @Test
    fun `한 조합에 같은 옵션값이 이미 두 번 붙어 있으면 실패한다`() {
        val url = seededDatabase("v16_dup_pair", duplicate = Duplicate.VALUE_PAIR)

        assertThatThrownBy { migrateTo(url, "16") }
            .describedAs("그룹 쪽 UNIQUE 는 먼저 걸리고 조합값 쪽에서 멈춘다")
            .isInstanceOf(FlywayException::class.java)

        assertThat(indexes(url, "option_groups"))
            .describedAs("앞 단계는 이미 적용됐다. 재실행 때 NOT EXISTS 가 그것을 건너뛴다")
            .contains("uk_option_group_product_name:1:product_id:0")
        assertThat(appliedSuccessfully(url, "16")).containsExactly("0")
    }

    private fun indexes(
        url: String,
        table: String,
    ): List<String> =
        query(
            url,
            "SELECT CONCAT_WS(':', INDEX_NAME, SEQ_IN_INDEX, COLUMN_NAME, NON_UNIQUE) " +
                "FROM information_schema.STATISTICS $IN_SCHEMA AND TABLE_NAME = '$table' " +
                "ORDER BY INDEX_NAME, SEQ_IN_INDEX",
        )

    private enum class Duplicate { NONE, GROUP_NAME, VALUE_PAIR }

    private fun seededDatabase(
        name: String,
        duplicate: Duplicate,
    ): String {
        val url = recreateDatabase(name)
        migrateBefore(url, "16")
        val secondGroupName = if (duplicate == Duplicate.GROUP_NAME) "크기" else "색상"
        val secondValueId = if (duplicate == Duplicate.VALUE_PAIR) 1 else 2
        execute(
            url,
            "CREATE TABLE option_groups (" +
                "id BIGINT NOT NULL AUTO_INCREMENT, product_id BIGINT NOT NULL, name VARCHAR(50) NOT NULL, " +
                "PRIMARY KEY (id)) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci",
            "CREATE TABLE option_combination_values (" +
                "id BIGINT NOT NULL AUTO_INCREMENT, option_combination_id BIGINT NOT NULL, " +
                "option_value_id BIGINT NOT NULL, PRIMARY KEY (id)" +
                ") ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci",
            "INSERT INTO option_groups (id, product_id, name) VALUES (1, 1, '크기'), (2, 1, '$secondGroupName')",
            "INSERT INTO option_combination_values (id, option_combination_id, option_value_id) VALUES " +
                "(1, 1, 1), (2, 1, $secondValueId)",
        )
        return url
    }
}
