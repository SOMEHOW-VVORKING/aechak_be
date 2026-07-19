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
                "SELECT table_name FROM information_schema.tables " +
                    "WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE'",
                String::class.java,
            )

        jdbcTemplate.execute(
            ConnectionCallback { connection ->
                connection.createStatement().use { st ->
                    st.execute("SET FOREIGN_KEY_CHECKS = 0")
                    try {
                        tables.forEach { st.execute("TRUNCATE TABLE `$it`") }
                    } finally {
                        st.execute("SET FOREIGN_KEY_CHECKS = 1")
                    }
                }
                null
            },
        )
    }
}
