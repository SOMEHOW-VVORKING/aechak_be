package com.aechak.api.inspection

import com.aechak.api.support.KafkaIntegrationTestBase
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [통합] 검수 테이블 마이그레이션이 빈 스키마에서 완주하는지 고정한다.
 *
 * 팀 공통 통합 테스트는 ddl-auto가 스키마를 만들어 Flyway를 타지 않으므로,
 * 마이그레이션 자체의 유효성은 Flyway를 켠 이 베이스에서만 검증된다.
 */
class ContentInspectionMigrationTest : KafkaIntegrationTestBase() {
    @Test
    fun `검수 테이블 넷이 생성된다`() {
        listOf("content_inspections", "inspection_findings", "ai_call_ledger", "product_review_queue_items").forEach { table ->
            val count =
                db
                    .sql(
                        """
                        SELECT COUNT(*) FROM information_schema.tables
                        WHERE table_schema = DATABASE() AND table_name = :name
                        """.trimIndent(),
                    ).param("name", table)
                    .query(Int::class.java)
                    .single()

            assertEquals(1, count, "$table 테이블이 없다. 마이그레이션이 적용되지 않았거나 이름이 다르다")
        }
    }

    @Test
    fun `같은 대상과 같은 내용은 두 번 기록되지 않는다`() {
        insertInspection(contentHash = "a".repeat(64))

        val duplicated =
            runCatching { insertInspection(contentHash = "a".repeat(64)) }.isFailure

        assertTrue(duplicated, "유일키가 없으면 같은 내용을 재판정해 모델 비용을 두 번 쓴다")
    }

    @Test
    fun `정책 버전이 다르면 다시 기록된다`() {
        insertInspection(contentHash = "b".repeat(64), policyVersion = "policy-v1")

        val accepted =
            runCatching {
                insertInspection(contentHash = "b".repeat(64), policyVersion = "policy-v2")
            }.isSuccess

        assertTrue(accepted, "정책을 고쳤는데 옛 판정을 재사용하면 바뀐 기준으로 보지 않는다")
    }

    @Test
    fun `관점별 결과는 검수당 한 행이고 재전달에 누적되지 않는다`() {
        val inspectionId = insertInspection(contentHash = "c".repeat(64))

        insertFinding(inspectionId, "DISPLAY_ADS")
        val duplicated = runCatching { insertFinding(inspectionId, "DISPLAY_ADS") }.isFailure

        assertTrue(duplicated, "누적되면 재전달마다 같은 위반이 쌓여 집계가 부풀려진다")
    }

    private fun insertInspection(
        contentHash: String,
        policyVersion: String = "policy-v1",
    ): Long {
        db
            .sql(
                """
                INSERT INTO content_inspections
                    (target_type, target_ref, content_hash, policy_version, processing_state,
                     attempt, requested_at, version, created_at, updated_at)
                VALUES ('PRODUCT', 42, :hash, :policy, 'QUEUED', 0, NOW(6), 0, NOW(6), NOW(6))
                """.trimIndent(),
            ).param("hash", contentHash)
            .param("policy", policyVersion)
            .update()

        return db
            .sql("SELECT id FROM content_inspections WHERE content_hash = :hash AND policy_version = :policy")
            .param("hash", contentHash)
            .param("policy", policyVersion)
            .query(Long::class.java)
            .single()
    }

    private fun insertFinding(
        inspectionId: Long,
        perspective: String,
    ) {
        db
            .sql(
                """
                INSERT INTO inspection_findings
                    (inspection_id, perspective, outcome, created_at, updated_at)
                VALUES (:id, :perspective, 'VIOLATION', NOW(6), NOW(6))
                """.trimIndent(),
            ).param("id", inspectionId)
            .param("perspective", perspective)
            .update()
    }
}
