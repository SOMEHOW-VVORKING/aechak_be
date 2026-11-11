package com.aechak.api.schema

import com.aechak.api.support.MigrationIntegrationTestBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * [통합] V107이 노출 게이트가 꺼져 있던 동안 위반 판정을 받은 상품만 REJECTED로 옮기는지 본다.
 *
 * 깨지면 둘 중 하나다. 위반으로 걸린 상품이 구매자 화면에 계속 남거나, 걸리지 않은 상품이
 * 구매자 화면에서 사라진다.
 *
 * 조건이 `products` 존재 여부인데 그 테이블은 Flyway가 만들지 않는다(ddl-auto 소유). 그래서
 * **CI에서는 이 UPDATE가 건너뛰어진다.** dev와 같은 모양의 products를 심은 DB에 따로 적용해 본다.
 */
class V107RejectProductsFlaggedBeforeGateMigrationIntegrationTest : MigrationIntegrationTestBase() {
    @Test
    fun `products가 없는 빈 DB에서는 건너뛰고 성공으로 기록된다`() {
        val url = recreateDatabase("v107_absent")

        migrateTo(url, "107")

        assertThat(appliedSuccessfully(url, "107"))
            .describedAs("V107이 성공으로 기록돼야 그 뒤 버전이 적용된다. CI가 지나는 경로다")
            .containsExactly("1")
    }

    @Test
    fun `열린 검토 행을 위반 판정이 막고 있는 상품만 REJECTED가 되고 version과 updated_at이 바뀐다`() {
        val url = seededDatabase("v107_reject")

        migrateTo(url, "107")

        assertThat(appliedSuccessfully(url, "107")).containsExactly("1")
        assertThat(products(url, "id IN ($FLAGGED, $FLAGGED_THEN_FAILED)"))
            .describedAs("위반 뒤에 판정 실패가 와도 행을 막고 있는 것은 여전히 그 위반이다")
            .containsExactly(
                "$FLAGGED:REJECTED:${SEEDED_VERSION + 1}:changed",
                "$FLAGGED_THEN_FAILED:REJECTED:${SEEDED_VERSION + 1}:changed",
            )
    }

    @Test
    fun `걸리지 않은 상품과 위반이 아닌 이유로 열린 행의 상품은 그대로다`() {
        val url = seededDatabase("v107_keep")

        migrateTo(url, "107")

        assertThat(products(url, "id NOT IN ($FLAGGED, $FLAGGED_THEN_FAILED)"))
            .describedAs("상태도 version도 updated_at도 심은 값 그대로여야 한다")
            .containsExactly(
                "$PASSED:APPROVED:$SEEDED_VERSION:kept",
                "$ADMIN_APPROVED:APPROVED:$SEEDED_VERSION:kept",
                "$SUPERSEDED:APPROVED:$SEEDED_VERSION:kept",
                "$ADMIN_REJECTED_THEN_PASSED:REJECTED:$SEEDED_VERSION:kept",
                "$OPEN_BY_PASS:APPROVED:$SEEDED_VERSION:kept",
                "$OPEN_WITHOUT_JUDGMENT:APPROVED:$SEEDED_VERSION:kept",
                "$FLAG_REPLACED_AT_SAME_TIME:APPROVED:$SEEDED_VERSION:kept",
                "$REVIEW_FLAG_ONLY:APPROVED:$SEEDED_VERSION:kept",
            )
    }

    @Test
    fun `다시 실행해도 결과가 같다`() {
        val url = seededDatabase("v107_idempotent")
        migrateTo(url, "107")
        val afterFirst = query(url, "SELECT CONCAT_WS(':', id, inspection_status, version, updated_at) FROM products ORDER BY id")

        // 운영에서 수동으로 다시 돌리거나 repair 뒤 재적용되는 경우를 본다.
        execute(url, "DELETE FROM flyway_schema_history WHERE version = '107'")
        migrateTo(url, "107")

        assertThat(query(url, "SELECT CONCAT_WS(':', id, inspection_status, version, updated_at) FROM products ORDER BY id"))
            .describedAs("이미 REJECTED인 상품을 다시 쓰지 않으므로 version도 그대로여야 한다")
            .isEqualTo(afterFirst)
    }

    /** dev 모양의 products와, 상품마다 다른 사정의 검수·검토 행을 심는다. */
    private fun seededDatabase(name: String): String {
        val url = recreateDatabase(name)
        migrateBefore(url, "107")
        execute(
            url,
            listOf(
                "CREATE TABLE products (" +
                    "id BIGINT NOT NULL AUTO_INCREMENT, inspection_status VARCHAR(30) NOT NULL, version INT NOT NULL, " +
                    "created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL, PRIMARY KEY (id)" +
                    ") ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci",
                product(FLAGGED, "APPROVED"),
                inspection(FLAGGED, "PRODUCT", "FLAG", T1),
                queueItem(FLAGGED, "PENDING_REVIEW", T1),
                product(FLAGGED_THEN_FAILED, "APPROVED"),
                inspection(FLAGGED_THEN_FAILED, "PRODUCT", "FLAG", T1),
                inspection(FLAGGED_THEN_FAILED, "PRODUCT", "FAILED", T2),
                queueItem(FLAGGED_THEN_FAILED, "PENDING_REVIEW", T1),
                product(PASSED, "APPROVED"),
                inspection(PASSED, "PRODUCT", "PASS", T1),
                // 관리자가 이미 풀어 준 위반. 닫힌 행은 대상이 아니다
                product(ADMIN_APPROVED, "APPROVED"),
                inspection(ADMIN_APPROVED, "PRODUCT", "FLAG", T1),
                queueItem(ADMIN_APPROVED, "APPROVED", T1),
                // 고친 내용이 통과해 닫힌 행
                product(SUPERSEDED, "APPROVED"),
                inspection(SUPERSEDED, "PRODUCT", "FLAG", T1),
                inspection(SUPERSEDED, "PRODUCT", "PASS", T2),
                queueItem(SUPERSEDED, "SUPERSEDED", T1),
                // 관리자가 반려한 상품의 재검수 통과. 통과 판정이 행을 다시 연다
                product(ADMIN_REJECTED_THEN_PASSED, "REJECTED"),
                inspection(ADMIN_REJECTED_THEN_PASSED, "PRODUCT", "FLAG", T1),
                queueItem(ADMIN_REJECTED_THEN_PASSED, "REJECTED", T1),
                inspection(ADMIN_REJECTED_THEN_PASSED, "PRODUCT", "PASS", T2),
                queueItem(ADMIN_REJECTED_THEN_PASSED, "PENDING_REVIEW", T2),
                // 아래 둘은 상태를 APPROVED로 심어 판정 조건만 따로 본다
                product(OPEN_BY_PASS, "APPROVED"),
                inspection(OPEN_BY_PASS, "PRODUCT", "PASS", T1),
                queueItem(OPEN_BY_PASS, "PENDING_REVIEW", T1),
                // 행의 시각에 맞는 검수가 없으면 건드리지 않는다
                product(OPEN_WITHOUT_JUDGMENT, "APPROVED"),
                inspection(OPEN_WITHOUT_JUDGMENT, "PRODUCT", "FLAG", T1),
                queueItem(OPEN_WITHOUT_JUDGMENT, "PENDING_REVIEW", T2),
                // 같은 시각의 검수가 둘이면 나중 것이 막고 있는 판정이다
                product(FLAG_REPLACED_AT_SAME_TIME, "APPROVED"),
                inspection(FLAG_REPLACED_AT_SAME_TIME, "PRODUCT", "FLAG", T1),
                inspection(FLAG_REPLACED_AT_SAME_TIME, "PRODUCT", "PASS", T1),
                queueItem(FLAG_REPLACED_AT_SAME_TIME, "PENDING_REVIEW", T1),
                // 같은 번호의 리뷰가 받은 위반 판정은 상품의 것이 아니다
                product(REVIEW_FLAG_ONLY, "APPROVED"),
                inspection(REVIEW_FLAG_ONLY, "REVIEW", "FLAG", T1),
                queueItem(REVIEW_FLAG_ONLY, "PENDING_REVIEW", T1),
            ),
        )
        return url
    }

    private fun product(
        id: Long,
        inspectionStatus: String,
    ): String =
        "INSERT INTO products (id, inspection_status, version, created_at, updated_at) " +
            "VALUES ($id, '$inspectionStatus', $SEEDED_VERSION, '$SEEDED_AT', '$SEEDED_AT')"

    /** 유일키에 내용 지문이 들어 있어 행마다 다른 지문을 준다. */
    private fun inspection(
        targetRef: Long,
        targetType: String,
        judgment: String,
        settledAt: String,
    ): String =
        "INSERT INTO content_inspections (target_type, target_ref, content_hash, policy_version, processing_state, " +
            "judgment, attempt, requested_at, settled_at, version, created_at, updated_at) " +
            "VALUES ('$targetType', $targetRef, SHA2(UUID(), 256), 'v1', 'SETTLED', '$judgment', 0, " +
            "'$SEEDED_AT', '$settledAt', 0, '$SEEDED_AT', '$SEEDED_AT')"

    private fun queueItem(
        productId: Long,
        status: String,
        blindedAt: String,
    ): String =
        "INSERT INTO product_review_queue_items (target_type, target_id, owner_id, block_source, status, blinded_at, " +
            "created_at, updated_at) " +
            "VALUES ('PRODUCT', $productId, 1, 'AI_DETECTION', '$status', '$blindedAt', '$SEEDED_AT', '$SEEDED_AT')"

    /** id:검수 상태:version:updated_at이 바뀌었는지. */
    private fun products(
        url: String,
        where: String,
    ): List<String> =
        query(
            url,
            "SELECT CONCAT_WS(':', id, inspection_status, version, IF(updated_at = '$SEEDED_AT', 'kept', 'changed')) " +
                "FROM products WHERE $where ORDER BY id",
        )

    companion object {
        private const val FLAGGED = 1L
        private const val FLAGGED_THEN_FAILED = 2L
        private const val PASSED = 3L
        private const val ADMIN_APPROVED = 4L
        private const val SUPERSEDED = 5L
        private const val ADMIN_REJECTED_THEN_PASSED = 6L
        private const val OPEN_BY_PASS = 7L
        private const val OPEN_WITHOUT_JUDGMENT = 8L
        private const val FLAG_REPLACED_AT_SAME_TIME = 9L
        private const val REVIEW_FLAG_ONLY = 10L

        private const val SEEDED_VERSION = 7
        private const val SEEDED_AT = "2026-01-01 00:00:00.000000"
        private const val T1 = "2026-02-01 10:00:00.123456"
        private const val T2 = "2026-02-02 10:00:00.654321"
    }
}
