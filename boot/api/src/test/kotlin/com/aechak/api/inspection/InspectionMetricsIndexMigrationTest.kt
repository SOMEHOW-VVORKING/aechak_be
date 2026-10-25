package com.aechak.api.inspection

import com.aechak.api.support.KafkaIntegrationTestBase
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * [통합] 지표 집계가 쓰는 인덱스가 두 스키마 경로에서 모두 만들어지는지 고정한다.
 *
 * 이 컨텍스트는 Flyway를 clean 후 전부 적용하고 나서 ddl-auto가 엔티티 선언대로 테이블을 다시
 * 만든다. 그래서 둘을 한 번에 본다 — 마이그레이션 자체의 유효성은 컨텍스트가 뜨는 것으로
 * 증명되고(V42의 조건부 생성 문장이 깨지면 migrate가 던져 부팅이 실패한다), 아래 단언은
 * ddl-auto 경로에 같은 이름의 인덱스가 선언돼 있는지를 본다.
 *
 * 한쪽만 두면 조회 계획이 환경 간에 갈린다. 테스트·로컬은 ddl-auto로 스키마를 만들고 dev는
 * Flyway와 ddl-auto를 함께 쓰므로, 엔티티에 선언이 없으면 그 환경만 인덱스 없이 돌아
 * "로컬에서는 빠른데 dev에서 느리다"가 된다. 반대로 마이그레이션에만 없으면 운영에 적용되지 않는다.
 */
class InspectionMetricsIndexMigrationTest : KafkaIntegrationTestBase() {
    @Test
    fun `지표가 쓰는 인덱스 둘이 만들어진다`() {
        mapOf(
            // 비율 전부의 앵커가 검수가 닫힌 시각이라 원장은 검수 id로 붙는다
            "ai_call_ledger" to "ix_ai_call_ledger_inspection",
            // 모든 비율의 분모가 "창 안에 닫힌 검수"다
            "content_inspections" to "ix_content_inspections_settled",
        ).forEach { (table, index) ->
            val count =
                db
                    .sql(
                        """
                        SELECT COUNT(DISTINCT INDEX_NAME) FROM INFORMATION_SCHEMA.STATISTICS
                        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = :table AND INDEX_NAME = :index
                        """.trimIndent(),
                    ).param("table", table)
                    .param("index", index)
                    .query(Int::class.java)
                    .single()

            assertEquals(1, count, "$table 에 $index 가 없다. 지표 집계가 그 테이블을 전체 스캔한다")
        }
    }

    /** 선두 칼럼이 승급률 분모의 진입 축이어야 한다. 순서가 바뀌면 관점으로 좁힐 수 없다 */
    @Test
    fun `원장 인덱스의 선두 칼럼이 검수 id다`() {
        val leading =
            db
                .sql(
                    """
                    SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.STATISTICS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_call_ledger'
                      AND INDEX_NAME = 'ix_ai_call_ledger_inspection'
                    ORDER BY SEQ_IN_INDEX
                    """.trimIndent(),
                ).query(String::class.java)
                .list()

        assertEquals(listOf("inspection_id", "perspective"), leading, "원장 인덱스의 칼럼 순서가 집계 진입 축과 다르다")
    }
}
