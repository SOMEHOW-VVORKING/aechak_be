-- PRD-01 셀러 상품 등록 착수 전 옵션 스키마 보강. UNIQUE 이름은 엔티티 @Table 선언과 맞춘다.
-- ERD v13이 요구하는 제약인데 엔티티에 선언이 없어 ddl-auto가 만들지 않고 있었다.
-- 대상 테이블은 아직 Flyway 관리 밖(ddl-auto 생성)이라, 테이블이 없는 새 DB에서는 건너뛴다 —
-- 그 경우 ddl-auto가 엔티티 선언대로 처음부터 만든다(V10 선례).
-- 기존 DB에 중복 행이 있으면 ALTER가 실패한다. 지우고 다시 돌린다.

-- 한 상품 안에서 옵션 그룹명은 유일. 지금은 요청 DTO만 막고 있어 다른 입구가 생기면 뚫린다.
SET @stmt = IF(
    EXISTS(
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'option_groups'
    ) AND NOT EXISTS(
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'option_groups'
          AND INDEX_NAME = 'uk_option_group_product_name'
    ),
    'ALTER TABLE option_groups ADD CONSTRAINT uk_option_group_product_name UNIQUE (product_id, name)',
    'SELECT 1'
);
PREPARE uk_group_stmt FROM @stmt;
EXECUTE uk_group_stmt;
DEALLOCATE PREPARE uk_group_stmt;

-- 한 조합에 같은 옵션값이 두 번 붙지 않는다. 붙으면 조합 서명과 연결 행이 어긋난다.
SET @stmt = IF(
    EXISTS(
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'option_combination_values'
    ) AND NOT EXISTS(
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'option_combination_values'
          AND INDEX_NAME = 'uk_option_combination_value_pair'
    ),
    'ALTER TABLE option_combination_values ADD CONSTRAINT uk_option_combination_value_pair UNIQUE (option_combination_id, option_value_id)',
    'SELECT 1'
);
PREPARE uk_pair_stmt FROM @stmt;
EXECUTE uk_pair_stmt;
DEALLOCATE PREPARE uk_pair_stmt;
