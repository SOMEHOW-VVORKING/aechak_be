-- ddl-auto=update는 컬럼을 지우지 않아, keyword로 통합하기 전의 NOT NULL 컬럼이 남은 DB에서는 INSERT가 기본값 없음(1364)으로 깨짐.
-- 두 컬럼이 없는 DB에서는 건너뜀.
SET @drop_display_keyword = IF(
    EXISTS(
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'recent_searches'
          AND COLUMN_NAME = 'display_keyword'
    ),
    'ALTER TABLE recent_searches DROP COLUMN display_keyword',
    'SELECT 1'
);
PREPARE drop_display_keyword_stmt FROM @drop_display_keyword;
EXECUTE drop_display_keyword_stmt;
DEALLOCATE PREPARE drop_display_keyword_stmt;

SET @drop_normalized_keyword = IF(
    EXISTS(
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'recent_searches'
          AND COLUMN_NAME = 'normalized_keyword'
    ),
    'ALTER TABLE recent_searches DROP COLUMN normalized_keyword',
    'SELECT 1'
);
PREPARE drop_normalized_keyword_stmt FROM @drop_normalized_keyword;
EXECUTE drop_normalized_keyword_stmt;
DEALLOCATE PREPARE drop_normalized_keyword_stmt;
