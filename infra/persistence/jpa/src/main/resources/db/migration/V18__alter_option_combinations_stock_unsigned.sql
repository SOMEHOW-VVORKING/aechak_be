-- ddl-auto=update는 컬럼 추가만 하고 타입은 안 바꿔서, 이미 int인 기존 테이블엔 엔티티의 int unsigned가 안 붙음.
-- 재고는 등록 검증과 changeStock의 0 하한이 0 이상만 만들므로 범위 밖 기존 행은 없음.
-- 테이블이 없는 새 DB에서는 ddl-auto가 엔티티대로 만들어 건너뜀.
SET @stmt = IF(
    EXISTS(
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'option_combinations'
          AND COLUMN_NAME = 'stock_quantity'
    ),
    'ALTER TABLE option_combinations MODIFY COLUMN stock_quantity INT UNSIGNED NOT NULL',
    'SELECT 1'
);
PREPARE alter_stmt FROM @stmt;
EXECUTE alter_stmt;
DEALLOCATE PREPARE alter_stmt;
