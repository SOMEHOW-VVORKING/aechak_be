package com.aechak.api.support

import org.springframework.jdbc.core.ConnectionCallback
import org.springframework.jdbc.core.JdbcTemplate

/**
 * FK 순서 문제를 피하려 FK 검사를 잠깐 끈 후 테이블을 비움
 */
class DatabaseCleaner(
    private val jdbcTemplate: JdbcTemplate,
) {
    fun truncateAll() {
        val tables =
            jdbcTemplate.queryForList(
                // flyway 이력은 지우지 않는다 — 지우면 다음 컨텍스트 부팅 때 마이그레이션이 재실행된다
                "SELECT table_name FROM information_schema.tables " +
                    "WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE' " +
                    "AND table_name <> 'flyway_schema_history'",
                String::class.java,
            )

        jdbcTemplate.execute(
            ConnectionCallback { connection ->
                connection.createStatement().use { st ->
                    st.execute("SET FOREIGN_KEY_CHECKS = 0")
                    try {
                        tables
                            .filter { st.executeQuery("SELECT 1 FROM `$it` LIMIT 1").use { rs -> rs.next() } }
                            .forEach { st.execute("TRUNCATE TABLE `$it`") }
                    } finally {
                        st.execute("SET FOREIGN_KEY_CHECKS = 1")
                    }
                }
                null
            },
        )
    }
}
