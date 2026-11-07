package com.aechak.api.schema

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.core.io.support.PathMatchingResourcePatternResolver

/**
 * ddl-auto 소유 테이블을 조건으로 다는 마이그레이션에 전용 테스트가 있는지 본다.
 *
 * ## 왜 이 가드가 필요한가
 *
 * `products`·`users` 같은 엔티티 테이블은 Flyway 가 만들지 않는다. ddl-auto 소유다. 그런데
 * Spring Boot 는 Flyway 를 Hibernate 보다 **먼저** 돌린다(`flywayInitializer` 가
 * `entityManagerFactory` 의 의존성). 그래서 빈 DB 에서 Flyway 가 돌 때 그 테이블들이 없다.
 *
 * 그 테이블을 건드리는 마이그레이션은 두 갈래로 갈린다.
 *
 *  - **조건부 `IF`/`PREPARE` 로 감싼 것** — 조건이 거짓이 되어 `SELECT 1` 로 떨어진다.
 *    **CI 를 통과하고 그 SQL 은 한 번도 실행되지 않는다.** dev 에서 처음 돌고 거기서 틀리면
 *    배포가 멈춘다.
 *  - **맨 DDL** — 그 자리에서 터져 CI 가 잡는다.
 *
 * 2026-11-07 에 둘 다 났다. `V105` 가 `ALGORITHM=INSTANT` 를 들고 조용히 건너뛰어
 * **dev 배포를 막았고**(SCRUM-375), `V106` 이 `users` FK 로 CI 에서 터졌다(SCRUM-373).
 * 전자는 사람이 ECS 로그를 읽어야 알았다.
 *
 * ## 이 테스트가 하는 일
 *
 * 마이그레이션을 전수로 읽어 **Flyway 가 만들지 않는 테이블을 조건으로 다는 파일**을 찾고,
 * 그 버전마다 `V<번호>...MigrationIntegrationTest` 가 있는지 본다. 없으면 아래 백로그에
 * 적혀 있어야 한다.
 *
 * **백로그는 늘어나서는 안 된다.** 새로 추가하는 조건부 마이그레이션은 테스트를 함께 써야
 * 한다. 백로그에 한 줄 더하는 것으로 이 가드를 피하지 마라 — 그러면 가드가 장식이 된다.
 */
class ConditionalMigrationCoverageTest {
    @Test
    fun `조건부 마이그레이션은 전용 테스트가 있거나 백로그에 있어야 한다`() {
        val conditional = conditionalOnDdlAutoOwnedTables()
        val covered = coveredVersions()
        val gap = conditional.keys - covered - KNOWN_UNCOVERED

        assertThat(gap)
            .describedAs(
                "ddl-auto 소유 테이블을 조건으로 다는데 전용 테스트가 없다. 그 SQL 은 CI 에서 한 번도 " +
                    "실행되지 않고 dev 에서 처음 돈다.\n" +
                    gap.sorted().joinToString("\n") { v ->
                        "  V$v — 조건 대상: ${conditional[v]?.sorted()?.joinToString(", ")}"
                    } +
                    "\n\nV105ProductAdminSaleBlockMigrationIntegrationTest 를 본떠 " +
                    "V<번호>...MigrationIntegrationTest 를 쓰라. 그 테스트는 빈 DB 에 앞 번호까지 올리고, " +
                    "조건 대상 테이블을 dev 형태로 만든 뒤, 그 마이그레이션 하나를 적용한다.",
            ).isEmpty()
    }

    @Test
    fun `테스트 이름의 번호는 실제 마이그레이션을 가리켜야 한다`() {
        val ghosts = coveredVersions() - allMigrations().keys

        assertThat(ghosts)
            .describedAs(
                "V<번호>...MigrationIntegrationTest 가 없는 번호를 가리킨다: " +
                    ghosts.sorted().joinToString(", ") { "V$it" } +
                    "\n번호 충돌로 마이그레이션을 다시 번호 딴 뒤 테스트 이름을 옮기지 않으면 이렇게 된다. " +
                    "그러면 이 가드가 실제 번호를 '테스트 없음'으로, 낡은 번호를 '있는 테스트'로 세어 " +
                    "양쪽으로 틀린다(실측: V66 → V72 재번호 때 그랬다). 클래스와 파일 이름을 새 번호로 옮기라.",
            ).isEmpty()
    }

    @Test
    fun `백로그에는 이미 커버된 버전이 남아 있어서는 안 된다`() {
        val stale = KNOWN_UNCOVERED intersect coveredVersions()

        assertThat(stale)
            .describedAs(
                "테스트를 쓴 버전이 백로그에 남아 있다. 지워야 남은 숫자가 실제 빚을 가리킨다: " +
                    stale.sorted().joinToString(", ") { "V$it" },
            ).isEmpty()
    }

    @Test
    fun `백로그는 늘어나서는 안 된다`() {
        assertThat(KNOWN_UNCOVERED)
            .describedAs(
                "백로그가 2026-11-07 기준 8건에서 늘었다. 새 조건부 마이그레이션은 테스트를 함께 써야 한다 — " +
                    "백로그에 더하는 것으로 가드를 피하면 가드가 장식이 된다.",
            ).hasSizeLessThanOrEqualTo(BACKLOG_CEILING)
    }

    @Test
    fun `스캔이 비어 있지 않다`() {
        // 이 단언이 없으면 리소스 경로가 바뀐 날 "빈틈 0건"으로 조용히 통과한다 — 있으나 없으나 같아진다.
        assertThat(allMigrations())
            .describedAs("마이그레이션을 %d개만 찾았다. 리소스 경로가 바뀌면 이 가드가 무력해진다", allMigrations().size)
            .hasSizeGreaterThanOrEqualTo(MIN_MIGRATIONS)

        assertThat(conditionalOnDdlAutoOwnedTables())
            .describedAs("조건부 마이그레이션을 하나도 못 찾았다. 조건 문법이 바뀌었는지 확인하라")
            .isNotEmpty()
    }

    /** 버전 → 그 파일이 조건으로 다는 ddl-auto 소유 테이블 이름들. */
    private fun conditionalOnDdlAutoOwnedTables(): Map<Int, Set<String>> {
        val migrations = allMigrations()
        val createdByFlyway =
            migrations
                .flatMap { (_, body) -> CREATE_TABLE.findAll(body).map { it.groupValues[1] } }
                .toSet()
        return migrations
            .mapValues { (_, body) ->
                GUARD_TABLE.findAll(body).map { it.groupValues[1] }.toSet() - createdByFlyway
            }.filterValues { it.isNotEmpty() }
    }

    /** 버전 → 파일 본문. */
    private fun allMigrations(): Map<Int, String> =
        PathMatchingResourcePatternResolver()
            .getResources("classpath*:db/migration/V*.sql")
            .mapNotNull { resource ->
                val version =
                    VERSION
                        .find(resource.filename.orEmpty())
                        ?.groupValues
                        ?.get(1)
                        ?.toIntOrNull()
                version?.let { it to resource.inputStream.use { stream -> stream.readBytes().decodeToString() } }
            }.toMap()

    /** `V<번호>...MigrationIntegrationTest` 클래스가 있는 버전들. */
    private fun coveredVersions(): Set<Int> =
        PathMatchingResourcePatternResolver()
            .getResources("classpath*:com/aechak/**/V*MigrationIntegrationTest.class")
            .mapNotNull {
                VERSION
                    .find(it.filename.orEmpty())
                    ?.groupValues
                    ?.get(1)
                    ?.toIntOrNull()
            }.toSet()

    companion object {
        private val VERSION = Regex("""^V(\d+)""")
        private val CREATE_TABLE = Regex("""CREATE\s+TABLE(?:\s+IF\s+NOT\s+EXISTS)?\s+`?([a-z_]+)`?""", RegexOption.IGNORE_CASE)
        private val GUARD_TABLE = Regex("""TABLE_NAME\s*=\s*'([a-z_]+)'""")

        /**
         * 전용 테스트가 아직 없는 조건부 마이그레이션. 2026-11-07 에 전수로 센 빚이다.
         *
         * 조건부 마이그레이션은 열다섯이고 그중 일곱(V34·V58·V72·V88·V91·V103·V105)은 이미 테스트가 있다.
         * 남은 여덟이 여기 있다. 하나씩 덮으며 이 목록을 줄인다.
         *
         * **새 버전을 더하지 마라.** 아래 `백로그는 늘어나서는 안 된다` 가 그것을 막는다.
         */
        private val KNOWN_UNCOVERED =
            setOf(7, 11, 12, 16, 17, 18, 20, 22)

        private const val BACKLOG_CEILING = 8

        /** 2026-11-07 기준 75개. 리소스 경로가 바뀌어 스캔이 비는 것을 잡는 하한이다. */
        private const val MIN_MIGRATIONS = 60
    }
}
