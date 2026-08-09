-- users.phone_hmac: 전체 번호 HMAC blind index — 점유 이전("1번호 1계정")의 검색 키이자 최후방어(UNIQUE).
-- 새 DB는 ddl-auto가 엔티티 선언대로 만들므로 기존 DB에만 델타를 적용한다(V7 선례).
SET @add_col = IF(
    EXISTS(
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'users'
    )
    AND NOT EXISTS(
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'users'
          AND COLUMN_NAME = 'phone_hmac'
    ),
    'ALTER TABLE users ADD COLUMN phone_hmac binary(32) NULL',
    'SELECT 1'
);
PREPARE add_col_stmt FROM @add_col;
EXECUTE add_col_stmt;
DEALLOCATE PREPARE add_col_stmt;

SET @add_uk = IF(
    EXISTS(
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'users'
    )
    AND NOT EXISTS(
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'users'
          AND INDEX_NAME = 'uk_users_phone_hmac'
    ),
    'ALTER TABLE users ADD CONSTRAINT uk_users_phone_hmac UNIQUE (phone_hmac)',
    'SELECT 1'
);
PREPARE add_uk_stmt FROM @add_uk;
EXECUTE add_uk_stmt;
DEALLOCATE PREPARE add_uk_stmt;
