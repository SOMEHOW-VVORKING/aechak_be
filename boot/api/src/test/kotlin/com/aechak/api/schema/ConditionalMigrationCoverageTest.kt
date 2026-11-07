package com.aechak.api.schema

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.core.io.support.PathMatchingResourcePatternResolver

/**
 * 조건이 그 시점에 반드시 거짓이 되는 마이그레이션에 전용 테스트가 있는지 본다.
 *
 * ## 왜 이 가드가 필요한가
 *
 * 마이그레이션이 테이블 존재를 조건으로 달 때, **그 조건이 CI에서 반드시 거짓이 되는** 경우가 있다.
 * 두 갈래다.
 *
 *  - **ddl-auto가 소유하는 테이블**(`users`·`products`·`cart_items` 등) — Spring Boot는 Flyway를
 *    Hibernate보다 먼저 돌린다(`flywayInitializer`가 `entityManagerFactory`의 의존성). 그래서 빈
 *    DB에서 Flyway가 돌 때 엔티티 테이블은 아직 없다.
 *  - **나중 번호가 만드는 테이블** — V20이 `order_items`를 조건으로 다는데 그 테이블은 V21이 만든다.
 *    V20이 도는 시점에는 없다.
 *
 * 어느 쪽이든 조건이 거짓이 되어 `SELECT 1`로 떨어진다. **CI를 통과하고 그 SQL은 한 번도
 * 실행되지 않는다.** dev에서 처음 돌고 거기서 틀리면 배포가 멈춘다.
 *
 * 2026-11-07에 V105가 `ALGORITHM=INSTANT`를 들고 그렇게 건너뛰어 dev 배포를 막았다(SCRUM-375).
 * 사람이 ECS 로그를 읽어야 알았다.
 *
 * ## 이 테스트가 하는 일
 *
 * 마이그레이션을 번호 순으로 읽어 각 버전마다 "그때까지 Flyway가 만든 테이블"을 누적하고, 그 집합에
 * 없는 테이블을 조건으로 다는 파일을 찾는다. 그 버전마다 `V<번호>...MigrationIntegrationTest`가
 * 있는지 보고, 없으면 아래 백로그에 적혀 있어야 한다.
 *
 * **백로그는 늘어나서는 안 된다.** 새로 추가하는 조건부 마이그레이션은 테스트를 함께 써야 한다.
 * 백로그에 한 줄 더하는 것으로 이 가드를 피하지 마라 — 그러면 가드가 장식이 된다.
 */
class ConditionalMigrationCoverageTest {
    @Test
    fun `그 시점에 없는 테이블을 조건으로 다는 마이그레이션은 전용 테스트가 있거나 백로그에 있어야 한다`() {
        val blind = conditionalOnAbsentTables()
        val gap = blind.keys - coveredVersions() - KNOWN_UNCOVERED

        assertThat(gap)
            .describedAs(
                "조건이 CI에서 반드시 거짓이 되는데 전용 테스트가 없다. 그 SQL은 한 번도 실행되지 않고 " +
                    "dev에서 처음 돈다.\n" +
                    gap.sorted().joinToString("\n") { "  V$it — 조건 대상: ${blind[it]?.sorted()?.joinToString(", ")}" } +
                    "\n\nV105ProductAdminSaleBlockMigrationIntegrationTest를 본떠 " +
                    "V<번호>...MigrationIntegrationTest를 쓰라. MigrationIntegrationTestBase를 상속하면 " +
                    "빈 DB를 만들고 앞 번호까지 올리는 절차가 준비돼 있다.",
            ).isEmpty()
    }

    @Test
    fun `테스트 이름의 번호는 실제 마이그레이션을 가리켜야 한다`() {
        val ghosts = coveredVersions() - allMigrations().keys

        assertThat(ghosts)
            .describedAs(
                "V<번호>...MigrationIntegrationTest가 없는 번호를 가리킨다: " +
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

        assertThat(conditionalOnAbsentTables())
            .describedAs("조건부 마이그레이션을 하나도 못 찾았다. 조건 문법이 바뀌었는지 확인하라")
            .isNotEmpty()
    }

    /**
     * 버전 → 그 버전이 조건으로 다는데 그 시점에 아직 없는 테이블 이름들.
     *
     * "그 시점에 있다"는 **자기 번호까지**가 만든 것을 뜻한다. 같은 파일 안에서 테이블을 만들고
     * 그 뒤에 조건을 다는 경우(V27·V29·V68)는 조건이 참이 되므로 사각이 아니다.
     */
    private fun conditionalOnAbsentTables(): Map<Int, Set<String>> {
        val migrations = allMigrations().toSortedMap()
        val createdThrough = mutableMapOf<Int, Set<String>>()
        val seen = mutableSetOf<String>()
        migrations.forEach { (version, body) ->
            seen += CREATE_TABLE.findAll(body).map { it.groupValues[1] }
            createdThrough[version] = seen.toSet()
        }
        return migrations
            .mapValues { (version, body) ->
                GUARD_TABLE.findAll(body).map { it.groupValues[1] }.toSet() - createdThrough.getValue(version)
            }.filterValues { it.isNotEmpty() }
    }

    /** 버전 → 파일 본문. */
    private fun allMigrations(): Map<Int, String> =
        PathMatchingResourcePatternResolver()
            .getResources("classpath*:db/migration/V*.sql")
            .mapNotNull { resource ->
                versionOf(resource.filename)?.let { version ->
                    version to resource.inputStream.use { stream -> stream.readBytes().decodeToString() }
                }
            }.toMap()

    /** `V<번호>...MigrationIntegrationTest` 클래스가 있는 버전들. */
    private fun coveredVersions(): Set<Int> =
        PathMatchingResourcePatternResolver()
            .getResources("classpath*:com/aechak/**/V*MigrationIntegrationTest.class")
            .mapNotNull { versionOf(it.filename) }
            .toSet()

    private fun versionOf(filename: String?): Int? =
        VERSION
            .find(filename.orEmpty())
            ?.groupValues
            ?.get(1)
            ?.toIntOrNull()

    companion object {
        private val VERSION = Regex("""^V(\d+)""")
        private val CREATE_TABLE =
            Regex("""CREATE\s+TABLE(?:\s+IF\s+NOT\s+EXISTS)?\s+`?([a-z_]+)`?""", RegexOption.IGNORE_CASE)
        private val GUARD_TABLE = Regex("""TABLE_NAME\s*=\s*'([a-z_]+)'""")

        /**
         * 전용 테스트가 아직 없는 조건부 마이그레이션. 2026-11-07에 전수로 센 빚이다.
         *
         * 조건이 반드시 거짓이 되는 마이그레이션은 열일곱이고 그중 아홉은 이미 테스트가 있다
         * (V20·V22·V34·V58·V72·V88·V91·V103·V105). 남은 여덟이 여기 있다. 하나씩 덮으며 이 목록을 줄인다.
         *
         * **새 버전을 더하지 마라.** 아래 `백로그는 늘어나서는 안 된다`가 그것을 막는다.
         */
        private val KNOWN_UNCOVERED = setOf(7, 10, 11, 12, 13, 16, 17, 18)

        private const val BACKLOG_CEILING = 8

        /** 2026-11-07 기준 75개. 리소스 경로가 바뀌어 스캔이 비는 것을 잡는 하한이다. */
        private const val MIN_MIGRATIONS = 60
    }
}
