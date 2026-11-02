package com.aechak.admin.support;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import javax.sql.DataSource;

/**
 * 행 잠금 대기를 DB에서 관찰한다 — 동시성 테스트가 "상대가 잠금 대기에 들어갈 시간"을 고정 sleep으로
 * 재지 않게 하는 장치.
 *
 * <p>고정 시간으로 재면 느린 러너에서 그 시간이 상대의 잠금 조회보다 먼저 끝난다. 그러면 앞선
 * 트랜잭션이 이미 커밋된 뒤에 상대가 읽으므로, <b>잠금이 없어도</b> 상대가 커밋된 상태를 보고 거절되어
 * 테스트가 거짓 통과한다. 관찰로 바꾸면 러너가 느릴수록 더 기다리고, 끝내 대기가 보이지 않으면
 * 조용히 통과하지 않고 끊는다.
 *
 * <p>잠금 대기 표(performance_schema)는 PROCESS 권한이 있어야 보이고 컨테이너의 테스트 전용 계정에는
 * 그 권한이 없어 root로 따로 접속한다 — V73NoticesMigrationIntegrationTest가 DB 생성 권한 때문에
 * root를 쓰는 것과 같은 선례다. 커넥션 풀을 쓰지 않는 것도 의도다: 관찰자가 풀의 마지막 커넥션을
 * 집으면 관찰 대상 요청이 굶는다.
 */
public final class LockWaitProbe {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final long POLL_INTERVAL_MILLIS = 20;

    // MySQLContainer의 기본 root 계정. 테스트 전용 계정에는 PROCESS 권한이 없다.
    private static final String ROOT_USER = "root";
    private static final String ROOT_PASSWORD = "test";

    private LockWaitProbe() {}

    /**
     * 주어진 테이블의 행 잠금을 기다리는 트랜잭션이 생길 때까지 기다린다 — 생기면 바로 돌아온다.
     * 제한 시간 안에 관찰되지 않으면 쓰기 경로가 그 행을 잠그지 않았다는 뜻이라 끊는다.
     */
    public static void awaitLockWaitOn(DataSource dataSource, String table) {
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        try (Connection root = rootConnection(dataSource);
                Statement statement = root.createStatement()) {
            while (System.nanoTime() < deadline) {
                if (lockWaiterCount(statement, table) > 0) {
                    return;
                }
                sleep(POLL_INTERVAL_MILLIS);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("잠금 대기를 관찰할 수 없습니다.", e);
        }
        throw new IllegalStateException(
                "%s 행의 잠금을 기다리는 트랜잭션이 %s 안에 관찰되지 않았습니다 — 쓰기 경로가 그 행을 잠그지 않았을 수 있습니다.".formatted(table, TIMEOUT));
    }

    /** 지금 이 테이블의 행 잠금을 기다리고 있는 요청 수. 대기가 없으면 표에 행이 생기지 않아 0이다. */
    private static int lockWaiterCount(Statement statement, String table) throws SQLException {
        String sql = "select count(*) from performance_schema.data_lock_waits w"
                + " join performance_schema.data_locks l on l.engine_lock_id = w.requesting_engine_lock_id"
                + " where l.object_name = '%s'";
        try (ResultSet rs = statement.executeQuery(sql.formatted(table))) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    /** 테스트 datasource가 가리키는 컨테이너에 root로 붙는다 — URL은 그 datasource에서 그대로 가져온다. */
    private static Connection rootConnection(DataSource dataSource) throws SQLException {
        String url;
        try (Connection probe = dataSource.getConnection()) {
            url = probe.getMetaData().getURL();
        }
        return DriverManager.getConnection(url, ROOT_USER, ROOT_PASSWORD);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
