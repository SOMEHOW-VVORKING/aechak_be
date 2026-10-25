package com.aechak.api.inspection

import com.aechak.domain.inspection.ProductReviewQueueItem
import jakarta.persistence.Table
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [단위] 검토 큐의 마이그레이션과 엔티티 선언이 어긋나지 않는지 본다.
 *
 * DB를 띄워서 보지 않는 이유가 있다. 통합 테스트 베이스는 Flyway를 돌린 뒤 `ddl-auto=create`로
 * 엔티티 테이블을 다시 만들기 때문에, 엔티티가 들고 있는 테이블의 실제 DDL은 언제나 엔티티
 * 메타데이터에서 나온다 — 거기서 information_schema를 읽어 엔티티와 비교하면 자기 자신을
 * 대조하는 항진명제가 된다. 그래서 마이그레이션 **파일**을 읽어 대조한다.
 *
 * 어긋나면 운영(Flyway)과 테스트·로컬(ddl-auto)이 서로 다른 인덱스로 돌아서, 어드민 큐 목록의
 * 정렬이 한쪽에서는 인덱스를 타고 다른 쪽에서는 파일소트가 된다. 느린 쪽이 운영일 수 있다.
 */
class ReviewQueueSchemaParityTest {
    @Test
    fun `마이그레이션이 만드는 인덱스를 엔티티도 같은 이름으로 선언한다`() {
        val inMigrations =
            INDEX_LINE
                .findAll(migrationText())
                .map { it.groupValues[1] }
                .toSet()
        val declared =
            ProductReviewQueueItem::class.java
                .getAnnotation(Table::class.java)
                .indexes
                .map { it.name }
                .toSet()

        assertTrue(inMigrations.isNotEmpty(), "마이그레이션에서 인덱스를 읽지 못했다. 파일 이름이나 구문이 바뀌었다")
        assertEquals(inMigrations, declared, "마이그레이션과 엔티티의 인덱스가 어긋나면 환경마다 조회 계획이 갈린다")
    }

    /**
     * 반려 시각의 출처가 `updated_at`(NOT NULL)에서 `resolved_at`(신규 NULL)으로 옮겨 갔다.
     * 백필이 없으면 이 파일 전에 닫힌 행의 반려 시각이 화면에서 전부 빈다 — 에러 없이 값만 사라진다.
     */
    @Test
    fun `이의 마이그레이션이 닫힌 시각을 과거 행에 백필한다`() {
        val text = resource(APPEAL_MIGRATION).replace(Regex("\\s+"), " ")

        assertTrue(
            text.contains("UPDATE product_review_queue_items SET resolved_at = updated_at"),
            "resolved_at 백필이 없으면 그 전에 닫힌 반려 이력의 시각이 전부 NULL로 나간다",
        )
    }

    private fun migrationText(): String = MIGRATIONS.joinToString("\n", transform = ::resource)

    private fun resource(name: String): String =
        checkNotNull(javaClass.getResourceAsStream("/db/migration/$name")) { "마이그레이션을 찾지 못했다: $name" }
            .use { it.readBytes().toString(Charsets.UTF_8) }

    private companion object {
        const val APPEAL_MIGRATION = "V41__add_review_queue_appeal.sql"

        /** 검토 큐 테이블을 만드는 파일과 그 뒤 칼럼·인덱스를 더하는 파일 전부 */
        val MIGRATIONS = listOf("V39__create_product_review_queue_items.sql", APPEAL_MIGRATION)

        /** `CREATE INDEX <이름> ON product_review_queue_items` — 조건부로 감싼 것도 같은 구문이다 */
        val INDEX_LINE =
            Regex("CREATE INDEX\\s+(\\S+)\\s+ON\\s+product_review_queue_items", RegexOption.IGNORE_CASE)
    }
}
