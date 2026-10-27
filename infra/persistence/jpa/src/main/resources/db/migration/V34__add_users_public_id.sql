-- users.public_id: 사용자 외부 식별자(26자). 기존 사용자는 첫 글자 0과 대문자 16진수 25자로 채운다.
-- users는 ddl-auto가 만드는 테이블이라 테이블이 없는 빈 DB에서는 아무것도 하지 않는다(V11과 같은 조건).

-- NOT NULL 변경이 남은 NULL을 빈 문자열로 바꾸지 않게 strict로 고정하고, 잠금 대기는 짧게 둔다. 끝에서 되돌린다.
SET @saved_sql_mode = @@SESSION.sql_mode;
SET SESSION sql_mode = IF(@@SESSION.sql_mode = '', 'STRICT_ALL_TABLES',
                          CONCAT(@@SESSION.sql_mode, ',STRICT_ALL_TABLES'));
SET @saved_lock_wait_timeout = @@SESSION.lock_wait_timeout;
SET SESSION lock_wait_timeout = 5;

-- 1. NULL 허용 컬럼 추가
SET @stmt = IF(
    EXISTS(
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users'
    )
    AND NOT EXISTS(
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND COLUMN_NAME = 'public_id'
    ),
    'ALTER TABLE users ADD COLUMN public_id VARCHAR(26) NULL, ALGORITHM=INSTANT',
    'SELECT 1'
);
PREPARE add_col_stmt FROM @stmt;
EXECUTE add_col_stmt;
DEALLOCATE PREPARE add_col_stmt;

-- 2~4는 컬럼이 아직 NULL 허용일 때만 실행한다. 다 끝난 DB에서 다시 돌아도 채운 값을 바꾸지 않는다.
SET @fill_pending = (
    SELECT IS_NULLABLE = 'YES' FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND COLUMN_NAME = 'public_id'
);

-- 2. 기본값 식 먼저 설정. 이후 public_id 없이 들어오는 가입 행도 값을 받는다
SET @stmt = IF(
    @fill_pending,
    'ALTER TABLE users ALTER COLUMN public_id SET DEFAULT (CONCAT(0, SUBSTRING(HEX(RANDOM_BYTES(13)), 1, 25)))',
    'SELECT 1'
);
PREPARE set_default_stmt FROM @stmt;
EXECUTE set_default_stmt;
DEALLOCATE PREPARE set_default_stmt;

-- 3. 기존 행 채우기
SET @stmt = IF(
    @fill_pending,
    'UPDATE users SET public_id = CONCAT(0, SUBSTRING(HEX(RANDOM_BYTES(13)), 1, 25)) WHERE public_id IS NULL',
    'SELECT 1'
);
PREPARE fill_stmt FROM @stmt;
EXECUTE fill_stmt;
DEALLOCATE PREPARE fill_stmt;

-- 4. NOT NULL 변경. 기본값 식은 함께 다시 건다
SET @stmt = IF(
    @fill_pending,
    'ALTER TABLE users MODIFY COLUMN public_id VARCHAR(26) NOT NULL DEFAULT (CONCAT(0, SUBSTRING(HEX(RANDOM_BYTES(13)), 1, 25))), ALGORITHM=INPLACE, LOCK=NONE',
    'SELECT 1'
);
PREPARE not_null_stmt FROM @stmt;
EXECUTE not_null_stmt;
DEALLOCATE PREPARE not_null_stmt;

-- 5. UNIQUE 추가
SET @stmt = IF(
    EXISTS(
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND COLUMN_NAME = 'public_id'
    )
    AND NOT EXISTS(
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND INDEX_NAME = 'uk_users_public_id'
    ),
    'ALTER TABLE users ADD UNIQUE KEY uk_users_public_id (public_id), ALGORITHM=INPLACE, LOCK=NONE',
    'SELECT 1'
);
PREPARE add_uk_stmt FROM @stmt;
EXECUTE add_uk_stmt;
DEALLOCATE PREPARE add_uk_stmt;

SET SESSION lock_wait_timeout = @saved_lock_wait_timeout;
SET SESSION sql_mode = @saved_sql_mode;
