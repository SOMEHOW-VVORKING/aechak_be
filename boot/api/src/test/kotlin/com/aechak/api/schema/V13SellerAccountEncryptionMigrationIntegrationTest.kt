package com.aechak.api.schema

import com.aechak.api.support.MigrationIntegrationTestBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * [통합] V13이 입점 신청의 평문 계좌번호 컬럼을 암호문 컬럼으로 바꾸는 경로를 본다.
 *
 * `seller_applications` 는 `V27` 에서야 Flyway 소유가 되므로 `V13` 이 도는 시점에는 ddl-auto 가
 * 만들거나 아예 없다. 그래서 **CI 에서는 두 단계가 건너뛰어진다.**
 *
 * 깨지면 평문 계좌번호가 그 컬럼에 남은 채로 배포된다.
 *
 * 둘째 단계는 컬럼이 **있으면** 값을 지운다. 개명 여부를 보지 않으므로 이미 전환된 DB 에서도 지운다.
 * 당시에는 미배포 기능이라 기존 값이 전부 무효였고 그것이 주석에 적힌 전제다. 지금은 사정이 달라
 * `flyway repair` 뒤 재적용이 일어나면 실제 계좌번호가 지워진다 — 아래 마지막 테스트가 그 동작을
 * 고정해 둔다.
 */
class V13SellerAccountEncryptionMigrationIntegrationTest : MigrationIntegrationTestBase() {
    @Test
    fun `테이블이 없는 빈 DB에서는 두 단계를 건너뛰고 성공으로 기록된다`() {
        val url = recreateDatabase("v13_absent")

        migrateTo(url, "13")

        assertThat(appliedSuccessfully(url, "13"))
            .describedAs("V13이 성공으로 기록돼야 그 뒤 버전이 적용된다. CI가 지나는 경로다")
            .containsExactly("1")
    }

    @Test
    fun `평문 컬럼이 암호문 컬럼으로 개명되고 남은 값이 비워진다`() {
        val url = seededDatabase("v13_convert", plaintextColumn = true)

        migrateTo(url, "13")

        assertThat(accountColumn(url))
            .describedAs("평문 컬럼 이름이 남으면 거기에 평문을 또 넣는 코드가 통과한다")
            .containsExactly("account_number_enc:varchar:256:YES")
        assertThat(query(url, "SELECT CONCAT(id, ':', COALESCE(account_number_enc, '<NULL>')) FROM seller_applications ORDER BY id"))
            .describedAs("개명만으로는 평문이 암호문 컬럼에 남는다. 전부 비워야 한다")
            .containsExactly("1:<NULL>", "2:<NULL>")
    }

    @Test
    fun `이미 암호문 컬럼인 DB에서는 개명을 건너뛴다`() {
        val url = seededDatabase("v13_already", plaintextColumn = false)

        migrateTo(url, "13")

        assertThat(accountColumn(url))
            .describedAs("컬럼이 하나여야 한다. 개명이 또 돌면 account_number 가 없어 실패한다")
            .containsExactly("account_number_enc:varchar:256:YES")
        assertThat(appliedSuccessfully(url, "13")).containsExactly("1")
    }

    @Test
    fun `이미 전환된 DB에 다시 돌리면 들어 있던 암호문도 지워진다`() {
        val url = seededDatabase("v13_rerun", plaintextColumn = false)
        execute(url, "UPDATE seller_applications SET account_number_enc = 'QUVTLWVuY3J5cHRlZA==' WHERE id = 1")

        execute(url, "DELETE FROM flyway_schema_history WHERE version = '13'")
        migrateTo(url, "13")

        assertThat(scalar(url, "SELECT COALESCE(account_number_enc, '<NULL>') FROM seller_applications WHERE id = 1"))
            .describedAs(
                "둘째 단계가 컬럼 존재만 보고 전부 지운다. 당시에는 기존 값이 전부 무효라는 전제가 맞았고 " +
                    "이 마이그레이션은 한 번만 적용된다. repair 뒤 재적용은 실제 계좌번호를 지운다",
            ).isEqualTo("<NULL>")
    }

    /** `컬럼명:타입:길이:NULL허용` 꼴. 계좌 관련 컬럼만 본다. */
    private fun accountColumn(url: String): List<String> =
        query(
            url,
            "SELECT CONCAT_WS(':', COLUMN_NAME, DATA_TYPE, CHARACTER_MAXIMUM_LENGTH, IS_NULLABLE) " +
                "FROM information_schema.COLUMNS $IN_SCHEMA AND TABLE_NAME = 'seller_applications' " +
                "AND COLUMN_NAME LIKE 'account_number%' ORDER BY COLUMN_NAME",
        )

    private fun seededDatabase(
        name: String,
        plaintextColumn: Boolean,
    ): String {
        val url = recreateDatabase(name)
        migrateBefore(url, "13")
        val accountColumn =
            if (plaintextColumn) "account_number VARCHAR(50) NULL" else "account_number_enc VARCHAR(256) NULL"
        execute(
            url,
            "CREATE TABLE seller_applications (" +
                "id BIGINT NOT NULL AUTO_INCREMENT, user_id BIGINT NOT NULL, status VARCHAR(30) NOT NULL, " +
                "$accountColumn, version BIGINT NOT NULL, " +
                "created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL, PRIMARY KEY (id)" +
                ") ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci",
        )
        val column = if (plaintextColumn) "account_number" else "account_number_enc"
        val values = if (plaintextColumn) "'110-1234-5678'" else "NULL"
        execute(
            url,
            "INSERT INTO seller_applications (id, user_id, status, $column, version, created_at, updated_at) VALUES " +
                "(1, 1, 'SUBMITTED', $values, 0, NOW(6), NOW(6)), " +
                "(2, 2, 'APPROVED', $values, 0, NOW(6), NOW(6))",
        )
        return url
    }
}
