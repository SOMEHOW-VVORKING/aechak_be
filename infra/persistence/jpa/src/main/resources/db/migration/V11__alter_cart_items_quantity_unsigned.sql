-- ddl-auto=update는 컬럼을 추가만 하고 타입 변경은 안 함.
-- 그대로 두면 엔티티의 smallint unsigned가 이미 int인 기존 테이블에 안 붙어 음수 방어가 생기지 않음.
-- 수량은 앱에서 1 이상 99 이하만 통과하므로 범위 밖 기존 행은 없음.
-- 테이블이 없는 새 DB에서는 Flyway가 먼저 돌고 ddl-auto가 엔티티대로 만들므로 건너뜀.
SET @stmt = IF(
    EXISTS(
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'cart_items'
          AND COLUMN_NAME = 'quantity'
    ),
    'ALTER TABLE cart_items MODIFY COLUMN quantity SMALLINT UNSIGNED NOT NULL',
    'SELECT 1'
);
PREPARE alter_stmt FROM @stmt;
EXECUTE alter_stmt;
DEALLOCATE PREPARE alter_stmt;
