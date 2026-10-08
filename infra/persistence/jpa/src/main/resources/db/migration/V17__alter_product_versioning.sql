-- PRD-04 셀러 상품 수정 착수에 맞춘 버전 이력 정리. UNIQUE 이름은 엔티티 @Table 선언과 맞춘다.
-- 1) product_versions에 UNIQUE(product_id, version_no) 추가. ERD가 요구하는데 엔티티에 선언이 없어
--    ddl-auto가 만들지 않고 있었다. 수정마다 version_no를 마지막 + 1로 채번하므로 동시 수정 둘이
--    같은 번호를 잡으면 이 제약이 막는다.
-- 2) product_versions.change_type 제거. 상태 변경은 status_snapshot 차이로 그대로 읽히고, 가격과
--    정보 변경은 이 값이 있어도 무엇이 바뀌었는지 말해주지 않는다.
-- 3) product_images에 노출 구간(from_version_no, to_version_no) 추가. 수정이 빠진 이미지를 지우는
--    대신 직전 버전으로 닫아, 어느 버전에서 무엇이 보였는지가 행으로 남는다. 기존 행은 등록 버전
--    1부터 노출 중인 것으로 채운다.
-- 대상 테이블은 아직 Flyway 관리 밖(ddl-auto 생성)이라 테이블이 없는 새 DB에서는 건너뛴다.
-- 그 경우 ddl-auto가 엔티티 선언대로 처음부터 만든다(V10, V14 선례).
-- 기존 DB에 중복 version_no 행이 있으면 ALTER가 실패한다. 지우고 다시 돌린다.

SET @stmt = IF(
    EXISTS(
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'product_versions'
    ) AND NOT EXISTS(
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'product_versions'
          AND INDEX_NAME = 'uk_product_version_no'
    ),
    'ALTER TABLE product_versions ADD CONSTRAINT uk_product_version_no UNIQUE (product_id, version_no)',
    'SELECT 1'
);
PREPARE uk_version_no_stmt FROM @stmt;
EXECUTE uk_version_no_stmt;
DEALLOCATE PREPARE uk_version_no_stmt;

SET @stmt = IF(
    EXISTS(
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'product_versions'
          AND COLUMN_NAME = 'change_type'
    ),
    'ALTER TABLE product_versions DROP COLUMN change_type',
    'SELECT 1'
);
PREPARE drop_change_type_stmt FROM @stmt;
EXECUTE drop_change_type_stmt;
DEALLOCATE PREPARE drop_change_type_stmt;

SET @stmt = IF(
    EXISTS(
        SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'product_images'
    ) AND NOT EXISTS(
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'product_images'
          AND COLUMN_NAME = 'from_version_no'
    ),
    'ALTER TABLE product_images ADD COLUMN from_version_no INT NOT NULL DEFAULT 1, ADD COLUMN to_version_no INT NULL',
    'SELECT 1'
);
PREPARE add_image_range_stmt FROM @stmt;
EXECUTE add_image_range_stmt;
DEALLOCATE PREPARE add_image_range_stmt;
