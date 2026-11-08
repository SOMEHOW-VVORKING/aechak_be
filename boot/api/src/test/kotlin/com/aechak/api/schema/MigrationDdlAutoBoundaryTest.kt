package com.aechak.api.schema

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.core.io.support.PathMatchingResourcePatternResolver

/**
 * 마이그레이션이 ddl-auto 소유 테이블을 맨 DDL로 참조하지 않는지 본다.
 *
 * Spring Boot는 Flyway를 Hibernate보다 먼저 돌린다. 그래서 Flyway가 돌 때 엔티티 테이블은
 * 아직 없다. 그 테이블에 FK를 거는 `CREATE TABLE`은 MySQL 1824
 * (`Failed to open the referenced table`)로 **마이그레이션 전체를 멈춘다** — 그 뒤 버전은
 * 하나도 적용되지 않는다.
 *
 * 2026-11-07에 V106이 `users`에 FK를 걸어 CI test 잡이 그 오류로 실패했다(SCRUM-373).
 * 레포 규약도 "BC 밖 참조도 같은 BC 참조도 인덱스만 둔다"라 FK 자체를 두지 않는 쪽인데,
 * 여기서는 규약을 넘어 동작 문제다.
 *
 * CI에서 잡히기는 하므로 조건부 쪽(ConditionalMigrationCoverageTest)보다 덜 위험하다. 다만
 * 왜 터지는지가 오류 문구만으로는 보이지 않아 진단에 시간이 든다. 이 테스트는 그 시간을 줄인다.
 */
class MigrationDdlAutoBoundaryTest {
    @Test
    fun `마이그레이션의 FK는 Flyway가 만드는 테이블만 가리켜야 한다`() {
        val createdByFlyway = tablesCreatedByFlyway()
        val violations =
            migrations().flatMap { (name, body) ->
                CREATE_TABLE_BLOCK.findAll(body).flatMap { block ->
                    val table = block.groupValues[1]
                    REFERENCES
                        .findAll(block.groupValues[2])
                        .map { it.groupValues[1] }
                        .filterNot { it in createdByFlyway }
                        .map { "$name: $table → $it" }
                }
            }

        assertThat(violations)
            .describedAs(
                "Flyway가 만들지 않는 테이블에 FK를 걸었다. Flyway는 Hibernate보다 먼저 돌므로 그 테이블이 " +
                    "아직 없고, MySQL 1824로 마이그레이션 전체가 멈춘다.\n" +
                    violations.joinToString("\n") { "  $it" } +
                    "\n\nFK를 지우고 인덱스만 두라(레포 규약도 그쪽이다).",
            ).isEmpty()
    }

    @Test
    fun `스캔이 비어 있지 않다`() {
        // 리소스 경로가 바뀌면 위 단언이 "위반 0건"으로 조용히 통과한다.
        assertThat(migrations()).hasSizeGreaterThanOrEqualTo(MIN_MIGRATIONS)
        assertThat(tablesCreatedByFlyway()).hasSizeGreaterThanOrEqualTo(MIN_TABLES)
    }

    private fun tablesCreatedByFlyway(): Set<String> =
        migrations()
            .flatMap { (_, body) -> CREATE_TABLE_BLOCK.findAll(body).map { it.groupValues[1] } }
            .toSet()

    /** 파일명 → 본문. */
    private fun migrations(): Map<String, String> =
        PathMatchingResourcePatternResolver()
            .getResources("classpath*:db/migration/V*.sql")
            .mapNotNull { resource ->
                resource.filename?.let { name ->
                    name to resource.inputStream.use { stream -> stream.readBytes().decodeToString() }
                }
            }.toMap()

    companion object {
        /**
         * `CREATE TABLE <이름> ( ... );` 한 덩이. 괄호 안을 2번 그룹으로 잡아 그 안의 FK만 본다.
         *
         * 조건부 `IF`로 감싼 ALTER의 FK는 보지 않는다 — 그쪽은 대상이 없으면 조건이 거짓이 되어
         * 건너뛰므로 1824가 나지 않는다. 그 사각은 ConditionalMigrationCoverageTest가 맡는다.
         */
        private val CREATE_TABLE_BLOCK =
            Regex(
                """CREATE\s+TABLE(?:\s+IF\s+NOT\s+EXISTS)?\s+`?([a-z_]+)`?\s*\((.*?)\)\s*(?:ENGINE|;)""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
            )
        private val REFERENCES = Regex("""REFERENCES\s+`?([a-z_]+)`?""", RegexOption.IGNORE_CASE)

        /** 2026-11-07 기준 마이그레이션 75개 / Flyway가 만드는 테이블 74개. */
        private const val MIN_MIGRATIONS = 60
        private const val MIN_TABLES = 60
    }
}
