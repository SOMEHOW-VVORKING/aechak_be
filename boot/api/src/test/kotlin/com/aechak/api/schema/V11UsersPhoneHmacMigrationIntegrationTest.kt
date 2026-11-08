package com.aechak.api.schema

import com.aechak.api.support.MigrationIntegrationTestBase
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.sql.SQLException

/**
 * [통합] V11이 users에 전화번호 HMAC 컬럼과 UNIQUE를 더하는 경로를 본다.
 *
 * `users` 는 Flyway 가 아예 만들지 않는다(ddl-auto 소유). 그래서 **CI 에서는 두 단계가
 * 건너뛰어진다.**
 *
 * 깨지면 "1번호 1계정" 불변식의 최후 방어선이 없는 채로 배포된다. 그 UNIQUE 가 동시 가입 race 를
 * 막는 마지막 수단이다.
 *
 * 컬럼을 새로 더하므로 기존 행은 전부 NULL 을 받고, MySQL 의 UNIQUE 는 NULL 을 여러 개 허용한다.
 * 그래서 **추가 시점에는 기존 데이터 때문에 실패할 일이 없다.** 아래 테스트가 그것과, 값이 들어온
 * 뒤에는 중복이 막힌다는 것을 함께 본다.
 */
class V11UsersPhoneHmacMigrationIntegrationTest : MigrationIntegrationTestBase() {
    @Test
    fun `users가 없는 빈 DB에서는 두 단계를 건너뛰고 성공으로 기록된다`() {
        val url = recreateDatabase("v11_absent")

        migrateTo(url, "11")

        assertThat(appliedSuccessfully(url, "11"))
            .describedAs("V11이 성공으로 기록돼야 그 뒤 버전이 적용된다. CI가 지나는 경로다")
            .containsExactly("1")
    }

    @Test
    fun `기존 행이 있어도 컬럼과 UNIQUE가 함께 생긴다`() {
        val url = seededDatabase("v11_apply")

        migrateTo(url, "11")

        assertThat(hmacColumn(url))
            .describedAs("전체 번호 HMAC 은 32바이트 고정이고, 기존 행을 채울 길이 없으므로 NULL 허용이어야 한다")
            .containsExactly("phone_hmac:binary:32:YES")
        assertThat(hmacIndex(url))
            .describedAs("UNIQUE 이름이 엔티티 선언과 같아야 제약 위반 번역이 그 이름을 찾는다")
            .containsExactly("uk_users_phone_hmac:1:phone_hmac:0")
        assertThat(query(url, "SELECT COUNT(*) FROM users WHERE phone_hmac IS NULL"))
            .describedAs("기존 행은 전부 NULL 이다. MySQL UNIQUE 가 NULL 다중을 허용하므로 추가가 실패하지 않는다")
            .containsExactly("3")
    }

    @Test
    fun `적용 뒤에는 같은 번호 HMAC을 두 번 넣을 수 없다`() {
        val url = seededDatabase("v11_duplicate")
        migrateTo(url, "11")

        execute(url, "UPDATE users SET phone_hmac = UNHEX(REPEAT('AB', 32)) WHERE id = 1")

        assertThatThrownBy {
            execute(url, "UPDATE users SET phone_hmac = UNHEX(REPEAT('AB', 32)) WHERE id = 2")
        }.describedAs("이 UNIQUE 가 1번호 1계정의 최후 방어선이다")
            .isInstanceOf(SQLException::class.java)
    }

    @Test
    fun `이미 적용된 DB에 다시 돌려도 컬럼과 UNIQUE가 그대로다`() {
        val url = seededDatabase("v11_idempotent")
        migrateTo(url, "11")

        execute(url, "DELETE FROM flyway_schema_history WHERE version = '11'")
        migrateTo(url, "11")

        assertThat(hmacColumn(url)).containsExactly("phone_hmac:binary:32:YES")
        assertThat(hmacIndex(url))
            .describedAs("NOT EXISTS 가드가 막으므로 UNIQUE 가 둘로 늘지 않아야 한다")
            .containsExactly("uk_users_phone_hmac:1:phone_hmac:0")
    }

    private fun hmacColumn(url: String): List<String> =
        query(
            url,
            "SELECT CONCAT_WS(':', COLUMN_NAME, DATA_TYPE, CHARACTER_MAXIMUM_LENGTH, IS_NULLABLE) " +
                "FROM information_schema.COLUMNS $IN_SCHEMA AND TABLE_NAME = 'users' AND COLUMN_NAME = 'phone_hmac'",
        )

    private fun hmacIndex(url: String): List<String> =
        query(
            url,
            "SELECT CONCAT_WS(':', INDEX_NAME, SEQ_IN_INDEX, COLUMN_NAME, NON_UNIQUE) " +
                "FROM information_schema.STATISTICS $IN_SCHEMA AND TABLE_NAME = 'users' " +
                "AND INDEX_NAME = 'uk_users_phone_hmac' ORDER BY SEQ_IN_INDEX",
        )

    private fun seededDatabase(name: String): String {
        val url = recreateDatabase(name)
        migrateBefore(url, "11")
        execute(
            url,
            "CREATE TABLE users (" +
                "id BIGINT NOT NULL AUTO_INCREMENT, status VARCHAR(30) NOT NULL, PRIMARY KEY (id)" +
                ") ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci",
            "INSERT INTO users (id, status) VALUES (1, 'ACTIVE'), (2, 'ACTIVE'), (3, 'WITHDRAWN')",
        )
        return url
    }
}
