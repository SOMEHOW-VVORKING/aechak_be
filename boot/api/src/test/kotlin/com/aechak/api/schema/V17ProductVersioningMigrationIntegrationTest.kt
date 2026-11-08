package com.aechak.api.schema

import com.aechak.api.support.MigrationIntegrationTestBase
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.flywaydb.core.api.FlywayException
import org.junit.jupiter.api.Test
import java.sql.SQLException

/**
 * [통합] V17이 상품 버전 이력을 정리하는 경로를 본다.
 *
 * `product_versions`·`product_images` 는 Flyway 가 아예 만들지 않는다(ddl-auto 소유). 그래서
 * **CI 에서는 세 단계가 건너뛰어진다.**
 *
 * 세 단계가 서로 독립 조건이라 일부만 적용된 DB 에서도 나머지가 돌아야 한다.
 *
 * 이미지 노출 구간은 주석이 "기존 행은 등록 버전 1부터 노출 중인 것으로 채운다"고 적는데, 백필
 * 문장이 따로 없고 `DEFAULT 1` 로 자동 채움에 기댄다. 그것이 실제로 그렇게 되는지를 아래에서 본다.
 */
class V17ProductVersioningMigrationIntegrationTest : MigrationIntegrationTestBase() {
    @Test
    fun `두 테이블이 없는 빈 DB에서는 세 단계를 건너뛰고 성공으로 기록된다`() {
        val url = recreateDatabase("v17_absent")

        migrateTo(url, "17")

        assertThat(appliedSuccessfully(url, "17"))
            .describedAs("V17이 성공으로 기록돼야 그 뒤 버전이 적용된다. CI가 지나는 경로다")
            .containsExactly("1")
    }

    @Test
    fun `버전 번호 UNIQUE가 생기고 change_type이 지워지고 이미지에 노출 구간이 더해진다`() {
        val url = seededDatabase("v17_apply", duplicateVersionNo = false)

        migrateTo(url, "17")

        assertThat(indexes(url, "product_versions"))
            .describedAs("동시 수정 둘이 같은 version_no 를 잡는 것을 이 UNIQUE 가 막는다")
            .contains(
                "uk_product_version_no:1:product_id:0",
                "uk_product_version_no:2:version_no:0",
            )
        assertThat(columns(url, "product_versions"))
            .describedAs("change_type 은 무엇이 바뀌었는지 말해주지 않아 지운다. 상태 변경은 status_snapshot 차이로 읽는다")
            .doesNotContain("change_type")
        assertThat(columns(url, "product_images"))
            .describedAs("수정에서 빠진 이미지를 지우는 대신 직전 버전으로 닫아 이력이 행으로 남는다")
            .contains("from_version_no", "to_version_no")
        assertThat(imageRanges(url))
            .describedAs("기존 행은 등록 버전 1부터 노출 중이어야 한다. 백필 문장이 없고 DEFAULT 1 이 그 일을 한다")
            .containsExactly("1:1:<NULL>", "2:1:<NULL>")
    }

    @Test
    fun `적용 뒤에는 같은 상품에 같은 version_no를 또 만들 수 없다`() {
        val url = seededDatabase("v17_guard", duplicateVersionNo = false)
        migrateTo(url, "17")

        assertThatThrownBy {
            execute(url, "INSERT INTO product_versions (id, product_id, version_no) VALUES (99, 1, 1)")
        }.describedAs("수정마다 마지막 + 1 로 채번하므로 동시 수정이 같은 번호를 잡을 수 있다")
            .isInstanceOf(SQLException::class.java)
    }

    @Test
    fun `같은 version_no가 이미 둘 있으면 UNIQUE를 걸지 못하고 실패한다`() {
        val url = seededDatabase("v17_duplicate", duplicateVersionNo = true)

        assertThatThrownBy { migrateTo(url, "17") }
            .describedAs("주석이 적은 대로 중복을 지우고 다시 돌려야 한다")
            .isInstanceOf(FlywayException::class.java)

        assertThat(appliedSuccessfully(url, "17")).containsExactly("0")
    }

    @Test
    fun `노출 구간이 이미 있는 DB에서는 그 단계를 건너뛰고 기존 값을 덮지 않는다`() {
        val url = recreateDatabase("v17_already")
        migrateBefore(url, "17")
        execute(
            url,
            "CREATE TABLE product_versions (" +
                "id BIGINT NOT NULL AUTO_INCREMENT, product_id BIGINT NOT NULL, version_no INT NOT NULL, " +
                "PRIMARY KEY (id)) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci",
            "CREATE TABLE product_images (" +
                "id BIGINT NOT NULL AUTO_INCREMENT, product_id BIGINT NOT NULL, " +
                "from_version_no INT NOT NULL DEFAULT 1, to_version_no INT NULL, PRIMARY KEY (id)" +
                ") ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci",
            "INSERT INTO product_images (id, product_id, from_version_no, to_version_no) VALUES (1, 1, 3, 5)",
        )

        migrateTo(url, "17")

        assertThat(imageRanges(url))
            .describedAs("이미 닫힌 구간을 1 로 되돌리면 어느 버전에서 무엇이 보였는지가 틀어진다")
            .containsExactly("1:3:5")
        assertThat(appliedSuccessfully(url, "17")).containsExactly("1")
    }

    /** `id:시작버전:끝버전` 꼴. */
    private fun imageRanges(url: String): List<String> =
        query(
            url,
            "SELECT CONCAT_WS(':', id, from_version_no, COALESCE(to_version_no, '<NULL>')) " +
                "FROM product_images ORDER BY id",
        )

    private fun columns(
        url: String,
        table: String,
    ): List<String> =
        query(
            url,
            "SELECT COLUMN_NAME FROM information_schema.COLUMNS $IN_SCHEMA " +
                "AND TABLE_NAME = '$table' ORDER BY COLUMN_NAME",
        )

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

    private fun seededDatabase(
        name: String,
        duplicateVersionNo: Boolean,
    ): String {
        val url = recreateDatabase(name)
        migrateBefore(url, "17")
        val secondVersionNo = if (duplicateVersionNo) 1 else 2
        execute(
            url,
            "CREATE TABLE product_versions (" +
                "id BIGINT NOT NULL AUTO_INCREMENT, product_id BIGINT NOT NULL, version_no INT NOT NULL, " +
                "change_type VARCHAR(30) NOT NULL, PRIMARY KEY (id)" +
                ") ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci",
            "CREATE TABLE product_images (" +
                "id BIGINT NOT NULL AUTO_INCREMENT, product_id BIGINT NOT NULL, PRIMARY KEY (id)" +
                ") ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci",
            "INSERT INTO product_versions (id, product_id, version_no, change_type) VALUES " +
                "(1, 1, 1, 'CREATED'), (2, 1, $secondVersionNo, 'PRICE')",
            "INSERT INTO product_images (id, product_id) VALUES (1, 1), (2, 1)",
        )
        return url
    }
}
