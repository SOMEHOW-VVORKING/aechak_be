package com.aechak.admin.support;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * FK 순서 문제를 피하려 FK 검사를 잠깐 끈 후, 데이터가 있는 테이블만 비움
 */
@RequiredArgsConstructor
public class DatabaseCleaner {

    private final JdbcTemplate jdbcTemplate;

    public void truncateAll() {
        List<String> tables = jdbcTemplate.queryForList(
                // flyway 이력은 지우지 않는다 — 지우면 다음 컨텍스트 부팅 때 마이그레이션이 재실행된다
                "SELECT table_name FROM information_schema.tables "
                        + "WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE' "
                        + "AND table_name <> 'flyway_schema_history'",
                String.class);

        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            try (Statement st = connection.createStatement()) {
                st.execute("SET FOREIGN_KEY_CHECKS = 0");
                try {
                    for (String table : tables) {
                        if (hasRows(st, table)) {
                            st.execute("TRUNCATE TABLE `" + table + "`");
                        }
                    }
                } finally {
                    st.execute("SET FOREIGN_KEY_CHECKS = 1");
                }
            }
            return null;
        });
    }

    private boolean hasRows(Statement st, String table) throws SQLException {
        try (ResultSet rs = st.executeQuery("SELECT 1 FROM `" + table + "` LIMIT 1")) {
            return rs.next();
        }
    }
}
