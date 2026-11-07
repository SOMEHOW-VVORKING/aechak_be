package com.aechak.api.schema

import com.aechak.api.support.MigrationIntegrationTestBase
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.flywaydb.core.api.FlywayException
import org.junit.jupiter.api.Test

/**
 * [통합] V10이 입점 신청 쪽 제약 여섯을 거는 경로를 본다.
 *
 * 조건 대상인 `seller_applications`·`application_documents`·`settlement_accounts` 는 `V27` 에서야
 * Flyway 소유가 된다. `V10` 이 도는 시점에는 ddl-auto 가 만들거나 아예 없다. 그래서
 * **CI 에서는 여섯 단계가 모두 건너뛰어진다.**
 *
 * 깨지면 유저당 신청 1행과 셀러당 정산계좌 1개라는 불변식의 DB 방어선이 없는 채로 배포된다.
 * 동시 INSERT 를 막는 최후 수단이 그 UNIQUE 들이다.
 */
class V10SellerApplicationConstraintsMigrationIntegrationTest : MigrationIntegrationTestBase() {
    @Test
    fun `테이블이 없는 빈 DB에서는 여섯 단계를 건너뛰고 성공으로 기록된다`() {
        val url = recreateDatabase("v10_absent")

        migrateTo(url, "10")

        assertThat(appliedSuccessfully(url, "10"))
            .describedAs("V10이 성공으로 기록돼야 그 뒤 버전이 적용된다. CI가 지나는 경로다")
            .containsExactly("1")
    }

    @Test
    fun `user_id가 NOT NULL로 조여지고 UNIQUE와 인덱스가 생긴다`() {
        val url = seededDatabase("v10_apply", duplicateUserId = false)

        migrateTo(url, "10")

        assertThat(userIdNullable(url))
            .describedAs("유저당 신청 1행 모델이라 user_id는 NULL일 수 없다")
            .isEqualTo("NO")
        assertThat(indexes(url, "seller_applications"))
            .describedAs("UNIQUE 하나와 어드민 목록·이력 대조 인덱스 둘이 엔티티 선언 이름으로 생겨야 한다")
            .contains(
                "uk_seller_applications_user_id:1:user_id:0",
                "ix_seller_applications_status_submitted_at:1:status:1",
                "ix_seller_applications_status_submitted_at:2:submitted_at:1",
                "ix_seller_applications_business_reg_no:1:business_reg_no:1",
            )
        assertThat(indexes(url, "application_documents"))
            .describedAs("서류는 신청서당 종류별 1장이다")
            .contains(
                "uk_application_documents_application_id_document_type:1:application_id:0",
                "uk_application_documents_application_id_document_type:2:document_type:0",
            )
        assertThat(indexes(url, "settlement_accounts"))
            .describedAs("셀러당 정산계좌 1개다")
            .contains("uk_settlement_accounts_seller_id:1:seller_id:0")
    }

    @Test
    fun `같은 user_id 신청이 둘 있으면 UNIQUE를 걸지 못하고 실패한다`() {
        val url = seededDatabase("v10_duplicate", duplicateUserId = true)

        assertThatThrownBy { migrateTo(url, "10") }
            .describedAs("주석이 적은 대로 중복을 지우고 다시 돌려야 하는 상황이다. 조용히 넘어가면 불변식이 뚫린 채 배포된다")
            .isInstanceOf(FlywayException::class.java)

        assertThat(appliedSuccessfully(url, "10"))
            .describedAs("실패로 기록돼 다음 배포가 같은 자리에서 다시 멈춰야 한다")
            .containsExactly("0")
    }

    @Test
    fun `이미 적용된 DB에 다시 돌려도 제약이 늘거나 깨지지 않는다`() {
        val url = seededDatabase("v10_idempotent", duplicateUserId = false)
        migrateTo(url, "10")
        val afterFirst = indexes(url, "seller_applications")

        execute(url, "DELETE FROM flyway_schema_history WHERE version = '10'")
        migrateTo(url, "10")

        assertThat(indexes(url, "seller_applications"))
            .describedAs("NOT EXISTS 가드가 막으므로 두 번 돌아도 같아야 한다")
            .isEqualTo(afterFirst)
    }

    private fun userIdNullable(url: String): String? =
        scalar(
            url,
            "SELECT IS_NULLABLE FROM information_schema.COLUMNS $IN_SCHEMA " +
                "AND TABLE_NAME = 'seller_applications' AND COLUMN_NAME = 'user_id'",
        )

    /** `인덱스명:순번:컬럼:비유일` 꼴. 마지막 0이 UNIQUE 다. */
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

    /**
     * V10 시점의 세 테이블을 ddl-auto 모양으로 만든다.
     *
     * `user_id` 를 NULL 허용으로 둔다 — 그것이 첫 단계의 조건이다. V10 은 테이블 존재가 아니라
     * **그 컬럼이 NULL 허용인지**를 보고 MODIFY 를 건다.
     */
    private fun seededDatabase(
        name: String,
        duplicateUserId: Boolean,
    ): String {
        val url = recreateDatabase(name)
        migrateBefore(url, "10")
        val secondUserId = if (duplicateUserId) 1 else 2
        execute(
            url,
            "CREATE TABLE seller_applications (" +
                "id BIGINT NOT NULL AUTO_INCREMENT, user_id BIGINT NULL, status VARCHAR(30) NOT NULL, " +
                "submitted_at DATETIME(6) NULL, business_reg_no VARCHAR(20) NULL, " +
                "account_number VARCHAR(50) NULL, version BIGINT NOT NULL, " +
                "created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL, PRIMARY KEY (id)" +
                ") ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci",
            "CREATE TABLE application_documents (" +
                "id BIGINT NOT NULL AUTO_INCREMENT, application_id BIGINT NOT NULL, " +
                "document_type VARCHAR(40) NOT NULL, created_at DATETIME(6) NOT NULL, " +
                "updated_at DATETIME(6) NOT NULL, PRIMARY KEY (id)" +
                ") ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci",
            "CREATE TABLE settlement_accounts (" +
                "id BIGINT NOT NULL AUTO_INCREMENT, seller_id BIGINT NOT NULL, " +
                "created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL, PRIMARY KEY (id)" +
                ") ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci",
            "INSERT INTO seller_applications " +
                "(id, user_id, status, submitted_at, business_reg_no, version, created_at, updated_at) VALUES " +
                "(1, 1, 'SUBMITTED', NOW(6), '1234567890', 0, NOW(6), NOW(6)), " +
                "(2, $secondUserId, 'APPROVED', NOW(6), '0987654321', 0, NOW(6), NOW(6))",
        )
        return url
    }
}
